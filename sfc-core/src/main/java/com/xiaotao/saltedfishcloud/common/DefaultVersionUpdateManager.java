package com.xiaotao.saltedfishcloud.common;

import com.xiaotao.saltedfishcloud.common.update.VersionUpdateHandler;
import com.xiaotao.saltedfishcloud.common.update.VersionUpdateManager;
import com.xiaotao.saltedfishcloud.service.config.version.Version;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class DefaultVersionUpdateManager implements VersionUpdateManager {
    private final static String GLOBAL_SCOPE = "GLOBAL_SCOPE";
    private final static String LOG_PREFIX = "[系统更新]";
    private final Map<String, TreeMap<Version, List<VersionUpdateHandler>>> allHandler;

    public DefaultVersionUpdateManager() {
        allHandler = new HashMap<>();
    }

    @Autowired(required = false)
    public synchronized void setVersionUpdateHandler(List<VersionUpdateHandler> handlerList) {
        if (handlerList == null || handlerList.isEmpty()) {
            return;
        }
        log.debug("{}从Spring Context中获取到的更新器数量：{}", LOG_PREFIX, handlerList.size());
        Map<String, Map<Version, List<VersionUpdateHandler>>> handlerMap = handlerList.stream()
                .collect(Collectors.groupingBy(
                        e -> Optional.ofNullable(e.getScope()).orElse(GLOBAL_SCOPE),
                        Collectors.groupingBy(VersionUpdateHandler::getUpdateVersion)
                ));
        handlerMap.forEach((scope, scopeHandlerList) -> {
            scopeHandlerList.forEach((version, versionUpdateHandlers) -> {
                List<VersionUpdateHandler> existList = allHandler
                        .computeIfAbsent(scope, k -> new TreeMap<>(Version::compareTo))
                        .computeIfAbsent(version, k -> new ArrayList<>());
                existList.addAll(versionUpdateHandlers);
            });
        });
    }

    @Override
    public synchronized void registerUpdateHandler(VersionUpdateHandler handler) {
        log.debug("{}注册[{}]的更新器：{}", LOG_PREFIX, handler.getUpdateVersion(),handler);
        List<VersionUpdateHandler> existList = allHandler
                .computeIfAbsent(Optional.ofNullable(handler.getScope()).orElse(GLOBAL_SCOPE), k -> new TreeMap<>(Version::compareTo))
                .computeIfAbsent(handler.getUpdateVersion(), k -> new ArrayList<>());

        existList.add(handler);
    }

    @Override
    public List<VersionUpdateHandler> getAllUpdateHandlerList() {
        return allHandler.values()
                .stream()
                .flatMap(e -> e.values().stream())
                .flatMap(Collection::stream)
                .sorted(Comparator.comparing(VersionUpdateHandler::getUpdateVersion))
                .collect(Collectors.toList());
    }

    @Override
    public List<VersionUpdateHandler> getNeedUpdateHandlerList(String scope, Version referVersion) {
        return allHandler.values()
                .stream()
                .flatMap(e -> e.values().stream())
                .flatMap(Collection::stream)
                .filter(e -> referVersion.isLessThen(e.getUpdateVersion()))
                .sorted(Comparator.comparing(VersionUpdateHandler::getUpdateVersion))
                .collect(Collectors.toList());
    }
}
