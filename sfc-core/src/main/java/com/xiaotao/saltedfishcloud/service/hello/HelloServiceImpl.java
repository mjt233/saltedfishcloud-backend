package com.xiaotao.saltedfishcloud.service.hello;

import com.xiaotao.saltedfishcloud.service.config.ConfigService;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import com.xiaotao.saltedfishcloud.utils.PropertyUtils;
import com.xiaotao.saltedfishcloud.utils.SFunc;
import com.xiaotao.saltedfishcloud.utils.TypeUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 问好服务默认实现类
 */
@SuppressWarnings("unchecked")
@Service
@Slf4j
public class HelloServiceImpl implements HelloService, ApplicationRunner {
    private final ConcurrentHashMap<String, Object> store = new ConcurrentHashMap<>();
    private final static String LOG_TITLE = "[Hello]";

    @Autowired(required = false)
    @Lazy
    private List<FeatureProvider> providers;

    @Autowired
    private ConfigService configService;

    @Override
    public void appendFeatureDetail(String name, Object detail) {
        log.debug("{}增加特性{}:{}", LOG_TITLE, name, detail);
        Object o = store.get(name);
        if (o == null) {
            o = new HashSet<>();
            ((Set<Object>)o).add(detail);
            store.put(name, o);
        } else if (o instanceof Collection) {
            ((Collection<Object>) o).add(detail);
        } else {
            throw new IllegalArgumentException("现有详情不是Collection的实现类，而是" + o.getClass().getName());
        }
    }

    @Override
    public void setFeature(String name, Object detail) {
        log.debug("{}设置特性{}:{}", LOG_TITLE, name, detail);
        store.put(name, detail);
    }

    /**
     * 移除特性
     */
    public void removeFeature(String name) {
        log.debug("{}移除特性{}", LOG_TITLE, name);
        store.remove(name);
    }

    @Override
    public Object getDetail(String name) {
        return store.get(name);
    }

    @Override
    public Map<String, Object> getAllFeatureDetail() {
        return store;
    }

    @Override
    public <T, R> void bindConfigAsFeature(SFunc<T, R> configKey, String mapKey) {
        PropertyUtils.ConfigFieldMeta meta = PropertyUtils.parseLambdaConfigNameMeta(configKey);
        this.bindConfigAsFeature(meta.getConfigName(), mapKey, meta.getField().getType());
    }

    @Override
    public void bindConfigAsFeature(String configKey, String mapKey, Class<?> type) {
        Consumer<String> valueHandler = configValue -> {
            Object value;
            try {
                if (configValue == null) {
                    removeFeature(configKey);
                    return;
                }
                if (Boolean.class.isAssignableFrom(type)) {
                    value = TypeUtils.toBoolean(configValue);
                } else if (Number.class.isAssignableFrom(type)) {
                    value = TypeUtils.toNumber(type, configValue);
                } else if (String.class.isAssignableFrom(type)) {
                    value = configValue;
                } else if (Collection.class.isAssignableFrom(type)) {
                    value = MapperHolder.parseJsonToList(configValue, Map.class);
                } else if (Map.class.isAssignableFrom(type)) {
                    value = MapperHolder.parseJsonToMap(configValue);
                } else {
                    value = MapperHolder.parseJson(configValue, type);
                }
                setFeature(mapKey, value);
            } catch (Exception e) {
                log.error("{}绑定更新错误，key：{} mapKey:{}", LOG_TITLE, configKey, mapKey, e);
            }
        };

        // 立即获取值
        valueHandler.accept(configService.getConfig(configKey));

        // 添加监听
        configService.addAfterSetListener(configKey, valueHandler);
    }

    protected void refresh() {
        if (providers != null) {
            for (FeatureProvider provider : providers) {
                provider.registerFeature(this);
            }
        }
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        refresh();
    }
}
