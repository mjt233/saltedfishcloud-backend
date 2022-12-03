package com.xiaotao.saltedfishcloud.common;

import com.xiaotao.saltedfishcloud.annotations.update.RollbackAction;
import com.xiaotao.saltedfishcloud.annotations.update.UpdateAction;
import com.xiaotao.saltedfishcloud.annotations.update.Updater;
import com.xiaotao.saltedfishcloud.common.update.VersionUpdateHandler;
import com.xiaotao.saltedfishcloud.common.update.VersionUpdateManager;
import com.xiaotao.saltedfishcloud.model.UpdateContext;
import com.xiaotao.saltedfishcloud.service.config.version.Version;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class DefaultVersionUpdateManager implements VersionUpdateManager, ApplicationContextAware {
    private final static String GLOBAL_SCOPE = "GLOBAL_SCOPE";
    private final static String LOG_PREFIX = "[系统更新]";
    private final Map<String, TreeMap<Version, List<VersionUpdateHandler>>> allHandler;
    private ApplicationContext applicationContext;
    private List<VersionUpdateHandler> annotationUpdateHandler;

    private boolean haveStructFromAnnotation = false;

    private Collection<Object> getUpdaterBeans() {
        return applicationContext.getBeansWithAnnotation(Updater.class).values();
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    public static class AnnotationActionMethod {
        public final UpdateAction updateAction;
        public final RollbackAction rollbackAction;
        public final Method method;
        public final Version version;

        public AnnotationActionMethod(UpdateAction updateAction, RollbackAction rollbackAction, Method method) {
            this.updateAction = updateAction;
            this.rollbackAction = rollbackAction;
            this.method = method;
            if (updateAction != null) {
                version = Version.valueOf(updateAction.value());
            } else if (rollbackAction != null) {
                version = Version.valueOf(rollbackAction.value());
            } else {
                version = null;
            }
        }

        public String getActionType() {
            if (updateAction == null && rollbackAction == null) {
                return null;
            }
            return updateAction != null ? "update" : "rollback";
        }
    }

    public static class AnnotationUpdaterFactory {
        private static Object[] getArgs(Method method, Version from, Version to) {
            Class<?>[] types = method.getParameterTypes();
            UpdateContext context = new UpdateContext();
            context.setFrom(from);
            context.setTo(to);
            if (types.length == 0) {
                return null;
            }
            Object[] args = new Object[types.length];
            for (int i = 0; i < types.length; i++) {
                if (types[i] == UpdateContext.class) {
                    args[i] = context;
                } else {
                    args[i] = null;
                }
            }
            return args;
        }
        public static List<VersionUpdateHandler> getFromAnnotationObj(Object obj) {
            Class<?> clazz = obj.getClass();
            Updater updater = clazz.getAnnotation(Updater.class);
            if (updater == null) {
                return null;
            }
            String scope = StringUtils.hasText(updater.value()) ? updater.value() : null;
            return Arrays.stream(clazz.getMethods())
                    .map(method -> {
                        UpdateAction updateAction = method.getAnnotation(UpdateAction.class);
                        RollbackAction rollbackAction = method.getAnnotation(RollbackAction.class);
                        return new AnnotationActionMethod(updateAction, rollbackAction, method);
                    })
                    .filter(method -> method.version != null)
                    .collect(Collectors.groupingBy(
                            method -> method.version,
                            Collectors.toMap(
                                AnnotationActionMethod::getActionType,
                                method -> method,
                                (oldVal, newVal) -> {
                                    String msg = clazz + "上的作用域[" + scope + "]的[" + oldVal.version + "]版本存在多个[" + oldVal.getActionType() + "]动作";
                                    throw new IllegalArgumentException(msg);
                                }
                            )
                    )).entrySet()
                    .stream()
                    .map(entry -> {
                        Version version = entry.getKey();
                        Map<String, AnnotationActionMethod> methodMap = entry.getValue();
                        log.debug("从注解构造更新程序：{} scope：{} version：{}", clazz, scope, version);
                        return new VersionUpdateHandler() {
                            @Override
                            public void update(Version from, Version to) throws Exception {
                                AnnotationActionMethod updateMethod = methodMap.get("update");
                                Object[] args = getArgs(updateMethod.method, from, to);
                                if (args == null) {
                                    updateMethod.method.invoke(obj);
                                } else {
                                    updateMethod.method.invoke(obj, args);
                                }
                            }

                            @Override
                            public Version getUpdateVersion() {
                                return version;
                            }

                            @Override
                            public void rollback(Version from, Version to) {
                                AnnotationActionMethod rollbackMethod = methodMap.get("rollback");
                                if (rollbackMethod != null) {
                                    try {
                                        Object[] args = getArgs(rollbackMethod.method, from, to);
                                        if (args == null) {
                                            rollbackMethod.method.invoke(obj);
                                        } else {
                                            rollbackMethod.method.invoke(obj, args);
                                        }
                                    } catch (Exception e) {
                                        throw new RuntimeException(e);
                                    }
                                }
                            }

                            @Override
                            public String getScope() {
                                return scope;
                            }
                        };
                    })
                    .sorted(Comparator.comparing(VersionUpdateHandler::getUpdateVersion))
                    .collect(Collectors.toList());
        }
    }


    /**
     * 从注解中获取
     * @return
     */
    public List<VersionUpdateHandler> getUpdateHandlerFromAnnotation() {
        if (annotationUpdateHandler != null) {
            return annotationUpdateHandler;
        }
        Collection<Object> updaterBeans = getUpdaterBeans();
        if (updaterBeans.isEmpty()) {
            log.info("没有来自@Updater构造的更新程序类");
            return Collections.emptyList();
        }
        annotationUpdateHandler = new ArrayList<>();
        for (Object bean : updaterBeans) {
            List<VersionUpdateHandler> handler = AnnotationUpdaterFactory.getFromAnnotationObj(bean);
            if (handler != null && !handler.isEmpty()) {
                annotationUpdateHandler.addAll(handler);
            }
        }
        return annotationUpdateHandler;
    }

    public DefaultVersionUpdateManager() {
        allHandler = new HashMap<>();
    }

    @Autowired(required = false)
    public synchronized void setVersionUpdateHandler(List<VersionUpdateHandler> handlerList) {
        if (handlerList == null || handlerList.isEmpty()) {
            return;
        }
        Map<String, Map<Version, List<VersionUpdateHandler>>> handlerMap = handlerList.stream()
                .collect(Collectors.groupingBy(
                        e -> StringUtils.hasText(e.getScope()) ? e.getScope() : GLOBAL_SCOPE,
                        Collectors.groupingBy(VersionUpdateHandler::getUpdateVersion)
                ));
        handlerMap.forEach((scope, scopeHandlerList) -> {
            scopeHandlerList.forEach((version, versionUpdateHandlers) -> {
                for (VersionUpdateHandler handler : versionUpdateHandlers) {
                    log.debug("{}在作用域[{}]注册[{}]的更新器：{}", LOG_PREFIX,scope, handler.getUpdateVersion(),handler);
                }
                List<VersionUpdateHandler> existList = allHandler
                        .computeIfAbsent(scope, k -> new TreeMap<>(Version::compareTo))
                        .computeIfAbsent(version, k -> new ArrayList<>());
                existList.addAll(versionUpdateHandlers);
            });
        });
    }

    @Override
    public synchronized void registerUpdateHandler(VersionUpdateHandler handler) {
        String scope = StringUtils.hasText(handler.getScope()) ? handler.getScope() : GLOBAL_SCOPE;
        log.debug("{}在作用域[{}]注册[{}]的更新器：{}", LOG_PREFIX,scope, handler.getUpdateVersion(),handler);
        List<VersionUpdateHandler> existList = allHandler
                .computeIfAbsent(scope, k -> new TreeMap<>(Version::compareTo))
                .computeIfAbsent(handler.getUpdateVersion(), k -> new ArrayList<>());

        existList.add(handler);
    }

    @Override
    public List<VersionUpdateHandler> getAllUpdateHandlerList() {
        registerAnnotationUpdateMethod();
        return allHandler.values()
                .stream()
                .flatMap(e -> e.values().stream())
                .flatMap(Collection::stream)
                .sorted(Comparator.comparing(VersionUpdateHandler::getUpdateVersion))
                .collect(Collectors.toList());
    }

    private synchronized void registerAnnotationUpdateMethod() {
        if (haveStructFromAnnotation) {
            return;
        }
        setVersionUpdateHandler(getUpdateHandlerFromAnnotation());
        haveStructFromAnnotation = true;
    }

    @Override
    public List<VersionUpdateHandler> getNeedUpdateHandlerList(String scope, Version referVersion) {
        registerAnnotationUpdateMethod();
        return allHandler
                .getOrDefault(Optional.ofNullable(scope).orElse(GLOBAL_SCOPE), new TreeMap<>())
                .values()
                .stream()
                .flatMap(Collection::stream)
                .filter(e -> referVersion.isLessThen(e.getUpdateVersion()))
                .sorted((a,b) -> b.getUpdateVersion().compareTo(a.getUpdateVersion()))
                .collect(Collectors.toList());
    }
}
