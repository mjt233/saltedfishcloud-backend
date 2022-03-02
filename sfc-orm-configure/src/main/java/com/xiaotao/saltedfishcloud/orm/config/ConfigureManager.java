package com.xiaotao.saltedfishcloud.orm.config;

import com.xiaotao.saltedfishcloud.orm.config.annotation.ConfigEntity;
import com.xiaotao.saltedfishcloud.orm.config.entity.MethodInst;
import com.xiaotao.saltedfishcloud.orm.config.exception.ConfigurationKeyNotExistException;
import com.xiaotao.saltedfishcloud.orm.config.exception.ConfigurationMapCallException;
import com.xiaotao.saltedfishcloud.orm.config.exception.ConfigurationException;
import com.xiaotao.saltedfishcloud.orm.config.utils.ConfigUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class ConfigureManager implements InitializingBean {
    private final ConcurrentHashMap<String, MethodInst> cache = new ConcurrentHashMap<>();

    @Autowired
    private ApplicationContext context;

    @Autowired
    private ConfigureHandler configureHandler;


    /**
     * 对配置节点的值进行设置
     * @param key   配置节点
     * @param val   配置值
     */
    @Transactional(rollbackFor = Exception.class)
    public void setConfig(String key, String val) throws ConfigurationException {
        try {
            final MethodInst methodInst = cache.get(key);
            if (methodInst == null) {
                throw new ConfigurationKeyNotExistException(key);
            }
            configureHandler.setConfig(key, val);
            methodInst.invoke(val);
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
            final List<String> keys = ConfigUtils.getAllConfigKey(v.getClass());
            for (String key : keys) {
                final MethodInst methodInst = ConfigUtils.getMethodInst(key, v);
                if (methodInst == null) {
                    log.warn("[ORM Config]无效节点：{}", key);
                    continue;
                }
                cache.put(key, methodInst);
                log.info("[ORM Config]注册配置节点：{}", key);

            }
        });
        log.info("[ORM Config]配置自动映射管理器初始化完成");
    }


}
