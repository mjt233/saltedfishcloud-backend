package com.xiaotao.saltedfishcloud.service.config;

import com.xiaotao.saltedfishcloud.config.SysProperties;
import com.xiaotao.saltedfishcloud.init.DatabaseInitializer;
import com.xiaotao.saltedfishcloud.init.DatabaseUpdater;
import com.xiaotao.saltedfishcloud.service.file.impl.store.LocalStoreConfig;
import com.xiaotao.saltedfishcloud.config.StoreType;
import com.xiaotao.saltedfishcloud.dao.mybatis.ConfigDao;
import com.xiaotao.saltedfishcloud.enums.ReadOnlyLevel;
import com.xiaotao.saltedfishcloud.entity.Pair;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 配置服务实现类
 * @TODO 监听机制实现线程安全
 */
@Service
@Slf4j
public class ConfigServiceImpl implements ConfigService {
    @Resource
    private ConfigDao configDao;
    @Resource
    private StoreTypeSwitch storeTypeSwitch;
    @Resource
    private LocalStoreConfig localStoreConfig;
    @Resource
    private SysProperties sysProperties;
    private final ArrayList<Consumer<Pair<ConfigName, String>>> listeners = new ArrayList<>();
    private final Map<ConfigName, List<Consumer<String>>> configListeners = new HashMap<>();

    @Override
    public void addConfigSetListener(Consumer<Pair<ConfigName, String>> listener) {
        listeners.add(listener);
    }

    @Override
    public void addConfigListener(ConfigName key, Consumer<String> listener) {
        List<Consumer<String>> consumers = configListeners.computeIfAbsent(key, k -> new LinkedList<>());
        consumers.add(listener);
    }

    /**
     * 获取存在的所有配置
     */
    @Override
    public Map<ConfigName, String> getAllConfig() {
        return configDao
                .getAllConfig()
                .stream()
                .map(e -> {
                    String key = e.getKey();
                    try {
                        ConfigName configName = ConfigName.valueOf(key);
                        return new Pair<>(configName, e.getValue());
                    } catch (IllegalArgumentException ignore) {
                        log.warn("[配置]未知的配置项：{}", key);
                        return null;
                    }
                }).filter(Objects::nonNull)
                .collect(
                        Collectors.toMap(
                                Pair::getKey,
                                Pair::getValue
                        )
                );
    }

    /**
     * 从配置表读取一个配置项的值
     * @param key   配置名
     * @return      结果
     */
    @Override
    public String getConfig(ConfigName key) {
        return configDao.getConfigure(key);
    }

    /**
     * 设置一个配置项
     * @TODO 将写死的配置响应事件使用监听器功能重构
     * @param key       配置项
     * @param value     配置值
     */
    @Override
    public boolean setConfig(ConfigName key, String value) throws IOException {
        // 未采用订阅者/发布者设计模式的代码，耦合度高，代码侵害度高
        switch (key) {
            case STORE_TYPE: return setStoreType(StoreType.valueOf(value));
            case REG_CODE: setInviteRegCode(value); return true;
            case SYNC_DELAY:
                LocalStoreConfig.SYNC_DELAY = Integer.parseInt(value);
                configDao.setConfigure(key, value);
            default:
                configDao.setConfigure(key, value);
        }

        // 发布更新消息到所有的订阅者（执行监听回调），大大降低耦合度，无代码侵害
        // @TODO 允许抛出异常中断执行
        for (Consumer<Pair<ConfigName, String>> listener : listeners) {
            listener.accept(new Pair<>(key, value));
        }
        for (Consumer<String> c : configListeners.getOrDefault(key, Collections.emptyList())) {
            c.accept(value);
        }
        return true;
    }
    /**
     * 设置一个配置项
     * @param key       配置项
     * @param value     配置值
     */
    @Override
    public boolean setConfig(String key, String value) throws IOException {
        return setConfig(ConfigName.valueOf(key), value);
    }

    /**
     * 设置存储类型
     * @param type 存储类型
     * @return true表示切换成功，false表示切换被忽略
     * @throws IllegalStateException 数据库配置表无相关信息
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean setStoreType(StoreType type) throws IOException {
        String origin = configDao.getConfigure(StoreType.getConfigKey());
        if (origin == null) {
            throw new IllegalStateException("数据配置表无信息，请重启服务器");
        }
        StoreType storeType = StoreType.valueOf(origin);
        if (storeType == type) {
            log.info("忽略的存储切换：{} -> {}", storeType, type);
            return false;
        }
        log.info("存储切换：{} -> {}", storeType, type.toString());
        try {
            LocalStoreConfig.setReadOnlyLevel(ReadOnlyLevel.DATA_MOVING);
            configDao.setConfigure(StoreType.getConfigKey(), type.toString());
            localStoreConfig.setStoreType(type.toString());
            storeTypeSwitch.switchTo(type);
            LocalStoreConfig.setReadOnlyLevel(null);
        } catch (IOException | RuntimeException e) {
            LocalStoreConfig.setReadOnlyLevel(null);
            throw e;
        }
        return true;
    }

    /**
     * 设置系统注册邀请码
     * @param code  邀请码
     */
    public void setInviteRegCode(String code) {
        configDao.setConfigure(ConfigName.REG_CODE, code);
        sysProperties.setRegCode(code);
    }
}
