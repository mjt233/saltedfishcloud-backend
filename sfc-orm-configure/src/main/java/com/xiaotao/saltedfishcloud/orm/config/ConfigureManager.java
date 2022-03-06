package com.xiaotao.saltedfishcloud.orm.config;

import com.xiaotao.saltedfishcloud.orm.config.annotation.ConfigEntity;
import com.xiaotao.saltedfishcloud.orm.config.entity.ConfigNodeHandler;
import com.xiaotao.saltedfishcloud.orm.config.exception.ConfigurationException;
import com.xiaotao.saltedfishcloud.orm.config.exception.ConfigurationKeyNotExistException;
import com.xiaotao.saltedfishcloud.orm.config.exception.ConfigurationMapCallException;
import com.xiaotao.saltedfishcloud.orm.config.utils.ConfigReflectUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class ConfigureManager implements InitializingBean {
    private final ConcurrentHashMap<String, ConfigNodeHandler> cache = new ConcurrentHashMap<>();

    @Autowired
    private ApplicationContext context;

    @Autowired
    private ConfigureHandler configureHandler;

    public void fetchConfig() throws ConfigurationException {
        for (RawConfigEntity config : configureHandler.getAllConfigByPrefix("")) {
            log.debug("[ORM Config]同步配置节点：{} 同步值：{}", config.getKey(), config.getValue());
            try {
                setConfig(config.getKey(), config.getValue());
            } catch (ConfigurationKeyNotExistException e) {
                log.warn("[ORM Config]未知的配置节点：{}", config.getKey());
            }
        }
    }

    /**
     * 对配置节点的值进行设置
     * @param key   配置节点
     * @param val   配置值
     */
    @Transactional(rollbackFor = Exception.class)
    public void setConfig(String key, String val) throws ConfigurationException {
        try {
            final ConfigNodeHandler configNodeHandler = cache.get(key);
            if (configNodeHandler == null) {
                throw new ConfigurationKeyNotExistException(key);
            }
            configureHandler.setConfig(key, val);
            configNodeHandler.set(val);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new ConfigurationMapCallException(e.getMessage(), e.getCause());
        }
    }

    /**
     * 获取所有系统中的可用配置节点
     * @return  配置节点只读集合
     */
    public Set<String> getAllConfigNode() {
        return cache.keySet();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        final Map<String, Object> beansWithAnnotation = context.getBeansWithAnnotation(ConfigEntity.class);
        beansWithAnnotation.forEach((k,v) -> {
            final List<String> keys = ConfigReflectUtils.getAllConfigKey(v.getClass());
            log.debug("[ORM Config]发现的配置类：{}", v.getClass().getName());
            for (String key : keys) {
                ConfigNodeHandler configNodeHandler = null;
                try {
                    configNodeHandler = ConfigReflectUtils.getMethodInst(key, v);
                } catch (NoSuchFieldException | NoSuchMethodException | IllegalAccessException | InvocationTargetException | InstantiationException e) {
                    e.printStackTrace();
                }
                if (configNodeHandler == null) {
                    log.warn("[ORM Config]无效节点：{}", key);
                    continue;
                }
                cache.put(key, configNodeHandler);
                log.info("[ORM Config]注册配置节点：{} 当前值：{}", key, configNodeHandler.get());

            }
        });
        fetchConfig();
        log.info("[ORM Config]配置自动映射管理器初始化完成");
    }


}
