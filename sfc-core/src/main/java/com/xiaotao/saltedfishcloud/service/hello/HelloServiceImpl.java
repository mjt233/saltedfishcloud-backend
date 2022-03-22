package com.xiaotao.saltedfishcloud.service.hello;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 问好服务默认实现类
 */
@SuppressWarnings("unchecked")
@Service
public class HelloServiceImpl implements HelloService, ApplicationRunner {
    private final ConcurrentHashMap<String, Object> store = new ConcurrentHashMap<>();

    @Autowired(required = false)
    @Lazy
    private List<FeatureProvider> providers;

    @Override
    public void appendFeatureDetail(String name, Object detail) {
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
        store.put(name, detail);
    }

    @Override
    public Object getDetail(String name) {
        return store.get(name);
    }

    @Override
    public Map<String, Object> getAllFeatureDetail() {
        return store;
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
