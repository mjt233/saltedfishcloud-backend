package com.xiaotao.saltedfishcloud.utils;

import com.xiaotao.saltedfishcloud.ext.PluginManager;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.util.ClassUtils;

import java.io.IOException;
import java.util.function.Supplier;

@UtilityClass
@Slf4j
public class SpringContextUtils {
    private static ConfigurableApplicationContext context;
    private static DefaultListableBeanFactory beanFactory;

    @Setter
    @Getter
    private static Supplier<SpringApplication> applicationFactory;

    @Setter
    @Getter
    private static Supplier<SpringApplication> emergencyApplicationFactory;

    @Setter
    @Getter
    private static String[] launchArgs;

    public static void setContext(ConfigurableApplicationContext context) {
        SpringContextUtils.context = context;
        beanFactory = (DefaultListableBeanFactory) context.getBeanFactory();
    }

    /**
     * 进入紧急模式
     */
    public static ConfigurableApplicationContext startEmergencyMode() {
        log.info("====== 进入紧急模式 ======");
        Thread thread = new Thread(() -> {
            long begin = System.currentTimeMillis();
            context = null;
            log.info("====== 系统启动紧急模式 ======");
            setContext(emergencyApplicationFactory.get().run(launchArgs));
            log.info("====== 紧急模式重启完成，耗时：{}s =======", (System.currentTimeMillis() - begin)/1000D);
        });

        thread.setDaemon(false);
        try {
            thread.start();
            thread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        return context;
    }

    public static PluginManager getPluginManager() {
        try {
            // 先关闭原来的插件管理器
            return getContext().getBean(PluginManager.class);
        } catch (NoSuchBeanDefinitionException e) {
            log.warn("没能获取到插件管理器");
            return null;
        }
    }

    /**
     * 根据启动参数和启动程序工厂执行快速重启
     */
    public static ConfigurableApplicationContext restart() {
        log.info("====== 服务重启 ======");
        Thread thread = new Thread(() -> {
            long begin = System.currentTimeMillis();
            PluginManager pluginManager = getPluginManager();
            log.info("====== 系统上下文关闭 ======");
            context.close();
            context = null;

            if (pluginManager != null) {
                try {
                    log.info("====== 插件系统关闭 ======");
                    pluginManager.close();
                } catch (IOException e) {
                    log.error("插件系统关闭异常: ", e);
                }
            }

            log.info("====== 系统重新启动 ======");
            setContext(applicationFactory.get().run(launchArgs));
            log.info("====== 系统重启完成，耗时：{}s =======", (System.currentTimeMillis() - begin)/1000D);
        });

        thread.setDaemon(false);
        try {
            thread.start();
            thread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        return context;
    }

    public static ConfigurableApplicationContext getContext() {
        return context;
    }

    public static void registerBean(Class<?> clazz) {
        String beanName = ClassUtils.getShortNameAsProperty(clazz);
        registerBean(beanName, clazz);
    }

    public static void registerBean(String beanName, Class<?> clazz) {
        AbstractBeanDefinition beanDefinition = BeanDefinitionBuilder.genericBeanDefinition(clazz)
                .setDependencyCheck(1)
                .setAutowireMode(AutowireCapableBeanFactory.AUTOWIRE_BY_TYPE)
                .getRawBeanDefinition();
        beanFactory.registerBeanDefinition(beanName, beanDefinition);
    }
}
