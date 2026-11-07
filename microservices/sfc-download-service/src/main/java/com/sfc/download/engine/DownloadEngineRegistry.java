package com.sfc.download.engine;

import com.sfc.common.exception.SfcJsonException;
import com.sfc.common.model.ConfigNode;
import com.sfc.common.utils.SfcStringUtils;
import com.sfc.download.constant.DownloadTaskConstants;
import com.sfc.download.model.DownloadTaskParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 下载引擎注册表：按下载类型路由到具体引擎，并提供创建期的统一校验、能力声明与类型推断。
 *
 * <p>容器中的全部 {@link DownloadEngine} Bean 在此建立「类型 → 引擎」索引，并校验两个装配约束：
 * 同一类型不允许被多个引擎声明、不同引擎不允许使用同一个 {@link DownloadEngine#getEngineId()}——
 * 任一约束被违反都会在启动时直接失败，避免运行时走到不确定的实现。引擎数量允许为 0（例如按配置裁剪了
 * 全部下载能力），此时注册表仍可正常装配，只是所有类型都会以「不支持」返回。</p>
 *
 * <p>任务处理器与业务接口都只依赖本类，不直接依赖任何具体引擎，因此新增下载方式时它们无需改动。</p>
 */
@Slf4j
@Component
public class DownloadEngineRegistry {

    /**
     * 类型推断的判定顺序：越靠前优先级越高。
     *
     * <p>带明确协议特征或路径后缀的类型排在前面，{@link DownloadType#HTTP} 永远最后兜底——任何
     * {@code http(s)} 地址都能被 HTTP 引擎接受，若排在前面会让 {@code .git}、{@code .torrent} 地址
     * 被误判为 HTTP。</p>
     */
    private static final List<DownloadType> DETECT_ORDER = List.of(
            DownloadType.MAGNET,
            DownloadType.ED2K,
            DownloadType.GIT,
            DownloadType.TORRENT,
            DownloadType.HTTP
    );

    /**
     * 下载来源协议校验（类型推断已下沉到各引擎的
     * {@link DownloadEngine#matchesSource(DownloadType, String)}）。
     */
    private final DownloadSourceResolver sourceResolver;

    /**
     * 类型到引擎的索引。
     */
    private final Map<DownloadType, DownloadEngine> engines;

    /**
     * 构造注册表。
     *
     * <p>使用 {@link ObjectProvider} 而非直接注入 {@code List<DownloadEngine>}：Spring 对「空的集合注入点」
     * 会按必需依赖处理并抛 {@code NoSuchBeanDefinitionException}，而本服务在未提供任何引擎时也应能装配。</p>
     *
     * @param engineProvider 容器中的全部下载引擎，允许没有
     * @param sourceResolver 下载来源协议校验
     * @throws IllegalStateException 引擎标识重复或同一下载类型被多个引擎声明时抛出
     */
    public DownloadEngineRegistry(ObjectProvider<DownloadEngine> engineProvider,
                                  DownloadSourceResolver sourceResolver) {
        this.sourceResolver = sourceResolver;
        Map<DownloadType, DownloadEngine> typeIndex = new EnumMap<>(DownloadType.class);
        Map<String, DownloadEngine> idIndex = new LinkedHashMap<>();
        engineProvider.orderedStream().forEach(engine -> register(typeIndex, idIndex, engine));
        this.engines = Collections.unmodifiableMap(typeIndex);
        log.info("{}下载引擎注册完成，引擎标识: {}，已支持类型: {}",
                DownloadTaskConstants.LOG_PREFIX, idIndex.keySet(), typeIndex.keySet());
    }

    /**
     * 把单个引擎声明的标识与类型登记到索引中。
     *
     * @param typeIndex 类型 → 引擎的索引
     * @param idIndex   引擎标识 → 引擎的索引
     * @param engine    下载引擎
     * @throws IllegalStateException 引擎缺少标识、标识重复或同一下载类型已被其它引擎声明时抛出
     */
    private static void register(Map<DownloadType, DownloadEngine> typeIndex,
                                 Map<String, DownloadEngine> idIndex,
                                 DownloadEngine engine) {
        if (engine == null) {
            return;
        }
        String engineId = SfcStringUtils.hasText(engine.getEngineId()) ? engine.getEngineId().trim() : null;
        if (engineId == null) {
            throw new IllegalStateException("下载引擎缺少唯一标识(engineId): " + engine.getClass().getName());
        }
        DownloadEngine sameIdEngine = idIndex.putIfAbsent(engineId, engine);
        if (sameIdEngine != null) {
            throw new IllegalStateException("下载引擎标识 " + engineId + " 重复: "
                    + sameIdEngine.getClass().getName() + " 与 " + engine.getClass().getName());
        }
        if (engine.supports() == null) {
            return;
        }
        for (DownloadType type : engine.supports()) {
            if (type == null) {
                continue;
            }
            DownloadEngine exists = typeIndex.putIfAbsent(type, engine);
            if (exists != null) {
                throw new IllegalStateException("下载类型 " + type.getCode() + " 被多个下载引擎声明: "
                        + exists.getClass().getName() + " 与 " + engine.getClass().getName());
            }
        }
    }

    /**
     * 按下载地址推断下载类型。
     *
     * <p>依次询问 {@link #DETECT_ORDER} 中各类型对应引擎的
     * {@link DownloadEngine#matchesSource(DownloadType, String)}，返回第一个命中的类型。判定只做纯语法
     * 猜测、不发起任何网络请求；该类型是否存在引擎、当前是否可用、来源是否合法，仍由
     * {@link #validate(DownloadTaskParams)} 在创建任务阶段判定。</p>
     *
     * @param source 下载来源（通常是用户填写的下载地址），允许为 {@code null} 或空白
     * @return 推断出的下载类型；来源为空、无对应引擎或所有引擎都不认领时返回 {@code null}
     */
    public DownloadType detect(String source) {
        if (!SfcStringUtils.hasText(source)) {
            return null;
        }
        for (DownloadType type : DETECT_ORDER) {
            DownloadEngine engine = engines.get(type);
            if (engine != null && engine.matchesSource(type, source)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 按下载类型获取引擎。
     *
     * @param type 下载类型
     * @return 对应的下载引擎
     * @throws SfcJsonException 类型为空或该类型没有注册引擎时抛出（HTTP 400 语义）
     */
    public DownloadEngine get(DownloadType type) {
        if (type == null) {
            throw new SfcJsonException(400, "下载类型不能为空");
        }
        DownloadEngine engine = engines.get(type);
        if (engine == null) {
            throw new SfcJsonException(400, "不支持的下载类型: " + type.getCode());
        }
        return engine;
    }

    /**
     * 创建任务阶段统一校验：类型必填 → 类型受支持 → 引擎可用 → 来源格式合法 → 引擎自身的参数校验。
     *
     * <p>任何一步失败都抛业务异常，调用方据此在<b>提交异步任务之前</b>拒绝请求，不会留下必然失败的记录。</p>
     *
     * @param params 下载任务参数
     * @throws SfcJsonException 类型缺失/不支持、引擎不可用或参数不合法时抛出（HTTP 400 语义）
     */
    public void validate(DownloadTaskParams params) {
        DownloadEngine engine = get(params == null ? null : params.type);
        String reason = engine.unavailableReason();
        if (SfcStringUtils.hasText(reason)) {
            throw new SfcJsonException(400, reason);
        }
        sourceResolver.validateSource(params.type, params);
        engine.validate(params);
    }

    /**
     * 获取全部已注册下载类型的能力声明。
     *
     * <p>按 {@link DownloadType} 的声明顺序输出；未注册引擎的类型不会出现在结果中。同一引擎支持多种类型时，
     * 每项会带 {@link DownloadCapability#getEngineId() 相同的引擎标识}。</p>
     *
     * @return 能力声明列表，无任何引擎时返回空列表
     */
    public List<DownloadCapability> capabilities() {
        List<DownloadCapability> capabilities = new ArrayList<>(engines.size());
        for (DownloadType type : DownloadType.values()) {
            DownloadEngine engine = engines.get(type);
            if (engine == null) {
                continue;
            }
            String reason = engine.unavailableReason();
            boolean available = !SfcStringUtils.hasText(reason);
            List<ConfigNode> options = engine.options();
            capabilities.add(DownloadCapability.builder()
                    .type(type)
                    .name(type.getDisplayName())
                    .engineId(engine.getEngineId())
                    .available(available)
                    .reason(available ? null : reason)
                    .options(options == null ? List.of() : options)
                    .build());
        }
        return capabilities;
    }
}
