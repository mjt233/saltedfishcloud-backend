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

import java.io.Closeable;
import java.io.IOException;

@UtilityClass
@Slf4j
public class SpringContextUtils {
    private static ConfigurableApplicationContext context;
    private static DefaultListableBeanFactory beanFactory;

    @Getter
    @Setter
    private static SpringApplication mainApplication;

    @Setter
    @Getter
    private static String[] launchArgs;

    public static void setContext(ConfigurableApplicationContext context) {
        SpringContextUtils.context = context;
        beanFactory = (DefaultListableBeanFactory) context.getBeanFactory();
    }

    public static ConfigurableApplicationContext restart() {
        log.info("====== 服务重启 ======");
        Thread thread = new Thread(() -> {
            long begin = System.currentTimeMillis();
            try {
                log.info("====== 插件系统关闭 ======");
                // 先关闭原来的插件管理器
                PluginManager pluginManager = getContext().getBean(PluginManager.class);
                pluginManager.close();
            } catch (NoSuchBeanDefinitionException e) {
                log.warn("没能获取到插件管理器");
            }
            catch (IOException e) {
                log.error("插件关闭出错：", e);
            }
            log.info("====== 系统上下文关闭 ======");
            context.close();
            context = null;

            log.info("====== 系统重新启动 ======");
            setContext(mainApplication.run(launchArgs));
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
