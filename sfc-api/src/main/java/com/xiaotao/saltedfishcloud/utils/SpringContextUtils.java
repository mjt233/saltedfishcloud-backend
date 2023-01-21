package com.xiaotao.saltedfishcloud.utils;

import lombok.experimental.UtilityClass;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.util.ClassUtils;

@UtilityClass
public class SpringContextUtils {
    private static ApplicationContext context;
    private static DefaultListableBeanFactory beanFactory;
    public static void setContext(ApplicationContext context) {
        ConfigurableApplicationContext configurableApplicationContext = (ConfigurableApplicationContext) context;
        beanFactory = (DefaultListableBeanFactory) configurableApplicationContext.getBeanFactory();
    }

    public static ApplicationContext getContext() {
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
