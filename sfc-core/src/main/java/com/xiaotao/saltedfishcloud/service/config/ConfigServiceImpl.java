package com.xiaotao.saltedfishcloud.service.config;

import com.xiaotao.saltedfishcloud.enums.StoreMode;
import com.xiaotao.saltedfishcloud.config.SysProperties;
import com.xiaotao.saltedfishcloud.config.SysRuntimeConfig;
import com.xiaotao.saltedfishcloud.dao.mybatis.ConfigDao;
import com.xiaotao.saltedfishcloud.ext.PluginManager;
import com.xiaotao.saltedfishcloud.model.ConfigNode;
import com.xiaotao.saltedfishcloud.model.NameValueType;
import com.xiaotao.saltedfishcloud.model.Pair;
import com.xiaotao.saltedfishcloud.enums.ProtectLevel;
import com.xiaotao.saltedfishcloud.model.PluginConfigNodeInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 配置服务实现类
 * TODO 监听机制实现线程安全
 */
@Service
@Slf4j
public class ConfigServiceImpl implements ConfigService {
    @Resource
    private ConfigDao configDao;
    @Resource
    private StoreTypeSwitch storeTypeSwitch;
    @Resource
    private SysProperties sysProperties;

    @Resource
    private PluginManager pluginManager;

    private final ArrayList<Consumer<Pair<String, String>>> listeners = new ArrayList<>();
    private final Map<String, List<Consumer<String>>> configBeforeSetListeners = new ConcurrentHashMap<>();
    private final Map<String, List<Consumer<String>>> configAfterSetListeners = new ConcurrentHashMap<>();

    @Override
    public void addConfigSetListener(Consumer<Pair<String, String>> listener) {
        listeners.add(listener);
    }

    @Override
    public void addBeforeSetListener(String key, Consumer<String> listener) {
        List<Consumer<String>> consumers = configBeforeSetListeners.computeIfAbsent(key, k -> new LinkedList<>());
        consumers.add(listener);
    }

    @Override
    public void addAfterSetListener(String key, Consumer<String> listener) {
        List<Consumer<String>> consumers = configAfterSetListeners.computeIfAbsent(key, k -> new LinkedList<>());
        consumers.add(listener);
    }

    @Override
    public List<PluginConfigNodeInfo> listPluginConfig() {
        Map<String, String> allConfig = getAllConfig();
        return pluginManager.listAllPlugin().stream().map(e -> {
            PluginConfigNodeInfo configNodeInfo = new PluginConfigNodeInfo();
            configNodeInfo.setAlias(e.getAlias());
            configNodeInfo.setName(e.getName());
            configNodeInfo.setIcon(e.getIcon());
            configNodeInfo.setGroups(pluginManager.getPluginConfigNodeGroup(e.getName()));
            configNodeInfo.getGroups().stream().flatMap(group -> group.getNodes().stream()).flatMap(group -> group.getNodes().stream()).forEach(node -> node.setValue(allConfig.get(node.getName())));
            return configNodeInfo;
        }).collect(Collectors.toList());
    }

    @Override
    public Map<String, String> listConfig(String operator, String keyPattern) {
        return Optional.ofNullable(configDao.listConfig(operator, keyPattern))
                .orElseGet(ArrayList::new)
                .stream()
                .collect(Collectors.toMap(Pair::getKey, Pair::getValue, (oldVal, newVal) -> newVal));
    }

    /**
     * 获取存在的所有配置
     */
    @Override
    public Map<String, String> getAllConfig() {
        Map<String, String> dbConfig = configDao.getAllConfig().stream().collect(Collectors.toMap(
                Pair::getKey,
                Pair::getValue
        ));

         return pluginManager.getAllPlugin()
                .keySet()
                .stream()
                .flatMap(e -> Optional.ofNullable(pluginManager.getPluginConfigNodeGroup(e)).orElse(Collections.emptyList()).stream())
                .flatMap(e -> e.getNodes().stream())
                .flatMap(e -> e.getNodes().stream())
                .collect(Collectors.toMap(
                        ConfigNode::getName,
                        e -> dbConfig.getOrDefault(e.getName(), e.getDefaultValue())
                ));

    }

    /**
     * 从配置表读取一个配置项的值
     * @param key   配置名
     * @return      结果
     */
    @Override
    public String getConfig(String key) {
        return configDao.getConfigure(key);
    }

    /**
     * 设置一个配置项
     * TODO 将写死的配置响应事件使用监听器功能重构
     * @param key       配置项
     * @param value     配置值
     */
    @Override
    @Transactional
    public boolean setConfig(String key, String value) {

        // 发布更新消息到所有的订阅者（执行监听回调），大大降低耦合度，无代码侵害
        // @TODO 允许抛出异常中断执行
        for (Consumer<Pair<String, String>> listener : listeners) {
            listener.accept(new Pair<>(key, value));
        }
        for (Consumer<String> c : configBeforeSetListeners.getOrDefault(key, Collections.emptyList())) {
            c.accept(value);
        }
        configDao.setConfigure(key, value);
        for (Consumer<String> c : configAfterSetListeners.getOrDefault(key, Collections.emptyList())) {
            c.accept(value);
        }
        return true;
    }

    @Override
    public boolean batchSetConfig(List<NameValueType<String>> configList) throws IOException {
        for (NameValueType<String> config : configList) {
            setConfig(config.getName(), config.getValue());
        }
        return false;
    }

    /**
     * 设置存储类型
     * @param type 存储类型
     * @return true表示切换成功，false表示切换被忽略
     * @throws IllegalStateException 数据库配置表无相关信息
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean setStoreType(StoreMode type) throws IOException {
        String origin = configDao.getConfigure(StoreMode.getConfigKey());
        if (origin == null) {
            throw new IllegalStateException("数据配置表无信息，请重启服务器");
        }
        StoreMode storeMode = StoreMode.valueOf(origin);
        if (storeMode == type) {
            log.info("忽略的存储切换：{} -> {}", storeMode, type);
            return false;
        }
        log.info("存储切换：{} -> {}", storeMode, type.toString());
        try {
            SysRuntimeConfig.getInstance().setProtectModeLevel(ProtectLevel.DATA_MOVING);
            configDao.setConfigure(StoreMode.getConfigKey(), type.toString());
            storeTypeSwitch.switchTo(type);
            SysRuntimeConfig.getInstance().setProtectModeLevel(ProtectLevel.OFF);
        } catch (IOException | RuntimeException e) {
            SysRuntimeConfig.getInstance().setProtectModeLevel(ProtectLevel.OFF);
            throw e;
        }
        return true;
    }
}
