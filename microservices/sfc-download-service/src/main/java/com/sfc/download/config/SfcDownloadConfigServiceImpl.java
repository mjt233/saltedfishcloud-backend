package com.sfc.download.config;

import com.sfc.cloud.remote.client.SfcCoreConfigFeignClient;
import com.sfc.common.annotations.ConfigProperty;
import com.sfc.common.annotations.ConfigPropertyEntity;
import com.sfc.common.model.ConfigNode;
import com.sfc.common.model.json.SfcJsonResult;
import com.sfc.common.utils.PropertyUtils;
import com.sfc.common.utils.SfcStringUtils;
import com.sfc.common.utils.SfcTypeUtils;
import com.sfc.download.model.property.Aria2Property;
import com.sfc.download.model.property.GitProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * {@link SfcDownloadConfigService} 的默认实现：读取 {@code sfc-core} 统一配置并绑定到配置实体。
 *
 * <p>日志口径：进入降级态时记一条 WARN，恢复时记一条 INFO，降级期间的持续失败只记 DEBUG，
 * 避免每 5 秒刷一条告警。</p>
 *
 * <p><b>暂时未做事件推送：</b>core 的 {@code ConfigService#addAfterSetListener} 是进程内事件，
 * 跨进程拿不到；本服务改用「TTL 缓存 + 懒刷新」，在
 * {@link SfcDownloadInternalProperties.Config#getRefreshIntervalMillis()} 内生效。</p>
 *
 * @see SfcDownloadConfigService
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SfcDownloadConfigServiceImpl implements SfcDownloadConfigService {

    /**
     * 日志前缀。
     */
    private static final String LOG_PREFIX = "[Download][Config]";

    /**
     * 参与统一配置读取的全部配置 key（类加载时按注解换算一次）。
     */
    private static final List<String> CONFIG_KEYS = List.copyOf(collectConfigKeys());

    /**
     * core 系统配置客户端。
     */
    private final SfcCoreConfigFeignClient configClient;

    /**
     * 本服务内部调优配置（读取刷新间隔）。
     */
    private final SfcDownloadInternalProperties internalProperties;

    /**
     * 快照刷新锁：保证同一时刻只有一个线程真正向 core 取值。
     */
    private final Object refreshLock = new Object();

    /**
     * 最近一次<b>成功</b>读取的配置快照。
     *
     * <p>降级时<b>不会</b>被覆盖，因此 core 抖动期间调用方继续看到最后一次成功的配置。</p>
     */
    private volatile Snapshot snapshot;

    /**
     * 全默认值兜底快照，仅在「从未成功读取过配置」的降级场景下使用。
     *
     * <p>懒构建一次后复用；永远不会写入 {@link #snapshot}，因此 core 恢复后不会被它污染。</p>
     */
    private volatile Snapshot fallbackSnapshot;

    /**
     * 降级状态下允许再次尝试读取 core 的时间戳（毫秒）。
     *
     * <p>用于抑制重试频率：一个 TTL 内最多尝试一次，避免每个请求都阻塞在不可达的 core 上。</p>
     */
    private volatile long retryAfterMillis;

    /**
     * 当前是否处于「core 配置不可用」的降级状态（表示状态本身，而非「是否已打过日志」）。
     */
    private volatile boolean degraded;

    /**
     * {@inheritDoc}
     */
    @Override
    public Aria2Property getAria2() {
        return current().aria2();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GitProperty getGit() {
        return current().git();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void refresh() {
        synchronized (refreshLock) {
            // 强制刷新：无视降级抑制窗口重新尝试一次；失败时保留已有快照不动
            retryAfterMillis = 0L;
            Snapshot loaded = reload();
            if (loaded != null) {
                snapshot = loaded;
            }
        }
    }

    /**
     * 获取当前有效的配置快照。
     *
     * <p>分支顺序：命中未过期成功快照 → 直接返回；否则若处于降级抑制窗口内 → 返回上次成功快照
     * （没有则返回兜底快照，且不写回）；否则加锁（双检）后真正向 core 刷新一次。</p>
     *
     * @return 配置快照，永不为 {@code null}
     */
    private Snapshot current() {
        Snapshot cached = snapshot;
        if (cached != null && cached.valid()) {
            return cached;
        }
        // 降级抑制窗口：不再打 core，直接用上次成功快照（或兜底快照）
        if (degraded && System.currentTimeMillis() < retryAfterMillis) {
            return cached != null ? cached : fallback();
        }
        synchronized (refreshLock) {
            cached = snapshot;
            if (cached != null && cached.valid()) {
                return cached;
            }
            if (degraded && System.currentTimeMillis() < retryAfterMillis) {
                return cached != null ? cached : fallback();
            }
            Snapshot loaded = reload();
            if (loaded != null) {
                snapshot = loaded;
                return loaded;
            }
            // 刷新失败：绝不覆盖 snapshot，只回退到「上次成功快照」或「兜底快照」
            cached = snapshot;
            return cached != null ? cached : fallback();
        }
    }

    /**
     * 向 core 读取一次配置并绑定出新的快照。
     *
     * <p>该方法<b>只</b>在成功时返回快照；失败时置位降级状态、刷新重试时间并返回 {@code null}，
     * 由调用方决定回退到哪个已有快照 —— 这是「降级不覆盖成功快照」的关键。</p>
     *
     * @return 新的成功快照；读取失败时返回 {@code null}
     */
    private Snapshot reload() {
        long ttl = refreshIntervalMillis();
        try {
            Map<String, String> values = fetchConfigValues();
            Aria2Property aria2 = new Aria2Property();
            GitProperty git = new GitProperty();
            bind(aria2, values);
            bind(git, values);
            if (degraded) {
                degraded = false;
                retryAfterMillis = 0L;
                log.info("{}sfc-core 统一配置已恢复可用", LOG_PREFIX);
            }
            log.debug("{}统一配置快照已刷新，本次读取到 {} 个配置项", LOG_PREFIX, values.size());
            return new Snapshot(aria2, git, System.currentTimeMillis() + ttl);
        } catch (RuntimeException e) {
            boolean firstEnterDegraded = !degraded;
            degraded = true;
            retryAfterMillis = System.currentTimeMillis() + ttl;
            if (firstEnterDegraded) {
                log.warn("{}读取 sfc-core 统一配置失败，将沿用最后一次成功的配置: {}", LOG_PREFIX, e.toString());
            } else {
                log.debug("{}读取 sfc-core 统一配置仍然失败: {}", LOG_PREFIX, e.toString());
            }
            return null;
        }
    }

    /**
     * 获取全默认值兜底快照（懒构建，构建后复用）。
     *
     * <p>仅用于「服务启动后 core 从未成功返回过配置」的场景。该快照不会被写入
     * {@link #snapshot}，因此不会影响后续成功读取的结果。</p>
     *
     * @return 使用注解默认值的快照，永不为 {@code null}
     */
    private Snapshot fallback() {
        Snapshot cached = fallbackSnapshot;
        if (cached != null) {
            return cached;
        }
        synchronized (refreshLock) {
            if (fallbackSnapshot == null) {
                Aria2Property aria2 = new Aria2Property();
                GitProperty git = new GitProperty();
                // 传入空 Map：所有字段都会回落到 @ConfigProperty#defaultValue()
                bind(aria2, Map.of());
                bind(git, Map.of());
                fallbackSnapshot = new Snapshot(aria2, git, Long.MAX_VALUE);
                log.debug("{}已构建兜底配置快照（注解默认值）", LOG_PREFIX);
            }
            return fallbackSnapshot;
        }
    }

    /**
     * 调用 core 批量读取全部配置 key 的值。
     *
     * @return 配置项名称到原始值的映射（core 只返回存在的配置项）
     * @throws RuntimeException core 调用失败或响应失败时抛出，由调用方降级处理
     */
    private Map<String, String> fetchConfigValues() {
        SfcJsonResult<Map<String, String>> result = configClient.batchGetConfig(CONFIG_KEYS);
        if (result == null) {
            throw new IllegalStateException("sfc-core 配置接口返回空响应");
        }
        result.requireSuccess();
        Map<String, String> data = result.getData();
        return data == null ? Map.of() : data;
    }

    /**
     * 把配置值绑定到配置实体上。
     *
     * @param target 配置实体实例
     * @param values 配置项名称到原始值的映射，允许为空 Map
     */
    private void bind(Object target, Map<String, String> values) {
        Class<?> entityClass = target.getClass();
        ConfigPropertyEntity entity = entityClass.getDeclaredAnnotation(ConfigPropertyEntity.class);
        if (entity == null) {
            log.warn("{}配置实体 {} 缺少 @ConfigPropertyEntity 注解，跳过绑定",
                    LOG_PREFIX, entityClass.getName());
            return;
        }
        for (Field field : entityClass.getDeclaredFields()) {
            ConfigProperty property = field.getDeclaredAnnotation(ConfigProperty.class);
            if (property == null) {
                continue;
            }
            String key = PropertyUtils.getConfigName(entity, property, field.getName());
            String raw = values.get(key);
            if (!SfcStringUtils.hasText(raw)) {
                raw = property.defaultValue();
            }
            try {
                field.setAccessible(true);
                field.set(target, SfcTypeUtils.convert(field.getType(), raw));
            } catch (IllegalAccessException | RuntimeException e) {
                // 单字段失败只影响该字段：保留实体字段初始值，并记录可定位的 key
                log.warn("{}配置项 {} 绑定失败，保留字段默认值: {}", LOG_PREFIX, key, e.toString());
            }
        }
    }

    /**
     * 获取配置快照的缓存有效期。
     *
     * @return 缓存有效期（毫秒），配置缺失或非法时返回默认值
     */
    private long refreshIntervalMillis() {
        SfcDownloadInternalProperties.Config config = internalProperties.getConfig();
        if (config == null || config.getRefreshIntervalMillis() <= 0L) {
            return SfcDownloadInternalProperties.Config.DEFAULT_REFRESH_INTERVAL_MILLIS;
        }
        return config.getRefreshIntervalMillis();
    }

    /**
     * 收集两个配置实体的全部配置项名称。
     *
     * @return 配置项名称列表
     */
    private static List<String> collectConfigKeys() {
        return Stream.concat(
                PropertyUtils.getConfigNodeFromEntityClass(Aria2Property.class).values().stream(),
                PropertyUtils.getConfigNodeFromEntityClass(GitProperty.class).values().stream()
        )
                .flatMap(n -> PropertyUtils.flattenNodes(Collections.singleton(n)).stream())
                .map(ConfigNode::getName)
                .toList();
    }

    /**
     * 配置快照。
     *
     * @param aria2    aria2 配置
     * @param git      Git 配置
     * @param expireAt 快照过期时间（毫秒时间戳）；兜底快照使用 {@link Long#MAX_VALUE}
     */
    private record Snapshot(Aria2Property aria2, GitProperty git, long expireAt) {

        /**
         * 判断快照是否仍在有效期内。
         *
         * @return 未过期返回 {@code true}
         */
        private boolean valid() {
            return System.currentTimeMillis() < expireAt;
        }
    }
}
