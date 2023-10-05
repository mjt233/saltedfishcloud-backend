package com.xiaotao.saltedfishcloud.service.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.xiaotao.saltedfishcloud.annotations.ConfigProperty;
import com.xiaotao.saltedfishcloud.annotations.ConfigPropertyEntity;
import com.xiaotao.saltedfishcloud.config.SysProperties;
import com.xiaotao.saltedfishcloud.config.SysRuntimeConfig;
import com.sfc.constant.MQTopic;
import com.sfc.constant.SysConfigName;
import com.xiaotao.saltedfishcloud.dao.mybatis.ConfigDao;
import com.sfc.enums.ProtectLevel;
import com.sfc.enums.StoreMode;
import com.xiaotao.saltedfishcloud.ext.PluginManager;
import com.xiaotao.saltedfishcloud.init.DatabaseInitializer;
import com.xiaotao.saltedfishcloud.model.NameValueType;
import com.xiaotao.saltedfishcloud.model.Pair;
import com.xiaotao.saltedfishcloud.model.PluginConfigNodeInfo;
import com.xiaotao.saltedfishcloud.service.MQService;
import com.xiaotao.saltedfishcloud.utils.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 配置服务实现类
 * TODO 监听机制实现线程安全
 * TODO 支持集群更新
 */
@Service
@Slf4j
public class ConfigServiceImpl implements ConfigService, InitializingBean {
    private static final String LOG_PREFIX = "[配置服务]";
    @Resource
    private ConfigDao configDao;
    @Resource
    private StoreTypeSwitch storeTypeSwitch;
    @Resource
    private SysProperties sysProperties;
    @Resource
    private DatabaseInitializer databaseInitializer;

    @Resource
    private PluginManager pluginManager;

    @Resource
    private MQService mqService;

    /**
     * 是否为开发环境
     */
    private Boolean isInDevelop = null;

    /**
     * 当配置key被修改后，抑制事件广播的key以确保某些操作只由单个节点执行。
     *
     */
    private final static List<String> suppressBroadcastKeys = List.of(SysConfigName.Store.SYS_STORE_TYPE);

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
        if (getIsInDevelop()) {
            pluginManager.refreshPluginConfig();
        }

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

        Map<String, String> res = new ConcurrentHashMap<>();
        pluginManager.getAllPlugin()
                .keySet()
                .stream()
                .flatMap(e -> Optional.ofNullable(pluginManager.getPluginConfigNodeGroup(e)).orElse(Collections.emptyList()).stream())
                .flatMap(e -> Optional.ofNullable(e.getNodes()).orElseGet(Collections::emptyList).stream())
                .flatMap(e -> Optional.ofNullable(e.getNodes()).orElseGet(Collections::emptyList).stream())
                 .forEach(e -> {
                     if (res.containsKey(e.getName())) {
                         throw new IllegalArgumentException("存在同名配置项 - " + e.getName() );
                     } else {
                         res.put(e.getName(), dbConfig.getOrDefault(e.getName(), Optional.ofNullable(e.getDefaultValue()).orElse("")));
                     }
                 });
        return res;
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
     * @param key       配置项
     * @param value     配置值
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean setConfig(String key, String value) {

        // 发布更新消息到所有的订阅者（执行监听回调），大大降低耦合度，无代码侵害
        // todo 允许抛出异常中断执行
        for (Consumer<Pair<String, String>> listener : listeners) {
            listener.accept(new Pair<>(key, value));
        }
        for (Consumer<String> c : configBeforeSetListeners.getOrDefault(key, Collections.emptyList())) {
            c.accept(value);
        }
        configDao.setConfigure(key, value);
        mqService.sendBroadcast(MQTopic.CONFIG_CHANGE, new NameValueType<>(key, value));
        return true;
    }

    /**
     * 订阅配置变更事件处理
     * todo 处理泛型json解析
     */
    @EventListener(ApplicationStartedEvent.class)
    @SuppressWarnings("unchecked")
    public void subscribeConfigSetEvent() {
        mqService.subscribeBroadcast(MQTopic.CONFIG_CHANGE, msg -> {
            try {
                NameValueType<String> nameValue = (NameValueType<String>)MapperHolder.parseAsJson(msg.getBody(), NameValueType.class);
                log.info("{}配置项{}设置值：{}", LOG_PREFIX, nameValue.getName(), nameValue.getValue());
                for (Consumer<String> c : configAfterSetListeners.getOrDefault(nameValue.getName(), Collections.emptyList())) {
                    try {
                        c.accept(nameValue.getValue());
                    } catch (Throwable e) {
                        log.error("{}配置项{}值设置后置处理出错，变更内容：{}，错误：{}", LOG_PREFIX, nameValue.getName(), nameValue.getValue(), e);
                    }
                }
            } catch (IOException e) {
                log.error("{}配置项值设置后置处理json解析出错，变更内容：{}，错误：{}", LOG_PREFIX, msg, e);
            }
        });
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

    @Override
    public void afterPropertiesSet() throws Exception {
        databaseInitializer.init();
    }


    private boolean getIsInDevelop() {
        if (isInDevelop == null) {
            isInDevelop = Arrays.asList(SpringContextUtils.getContext().getEnvironment().getActiveProfiles())
                    .contains("develop");
        }
        return isInDevelop;
    }

    @Override
    public void bindPropertyEntity(Object bean) {
        ConfigPropertyEntity entity = bean.getClass().getAnnotation(ConfigPropertyEntity.class);
        for (Field field : ClassUtils.getAllFields(bean.getClass())) {
            ConfigProperty property = field.getAnnotation(ConfigProperty.class);
            if (property == null) {
                continue;
            }
            String configName = PropertyUtils.getConfigName(entity, property, field.getName());
            field.setAccessible(true);
            Consumer<String> configConsumer = newVal -> {
                try {
                    if (newVal == null) {
                        String defaultValue = property.defaultValue();
                        log.warn("{}配置项{}设置了null值，将设定回默认值：{}", LOG_PREFIX, configName, defaultValue);
                        field.set(bean, TypeUtils.convert(field.getType(), defaultValue));
                    } else {
                        field.set(bean, TypeUtils.convert(field.getType(), newVal));
                    }
                } catch (IllegalAccessException e) {
                    log.error("{}绑定配置实体字段 [{}] 值设置失败, 配置项:{}", LOG_PREFIX, field.getName(), configName, e);
                }
            };

            addAfterSetListener(configName, configConsumer);
            configConsumer.accept(getConfig(configName));
        }
    }
}

