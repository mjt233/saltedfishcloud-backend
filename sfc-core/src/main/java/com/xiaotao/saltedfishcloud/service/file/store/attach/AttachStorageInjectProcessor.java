package com.xiaotao.saltedfishcloud.service.file.store.attach;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

/**
 * {@link AttachStorageInject} 注解处理器，负责自动注入 {@link AttachStorage} 实例。
 * <p>
 * 扫描所有 Bean 中被 {@code @AttachStorageInject} 标注的字段，自动完成存储域注册和存储实例注入。
 */
@Component
public class AttachStorageInjectProcessor implements BeanPostProcessor {

    private final AttachStorageManager attachStorageManager;

    public AttachStorageInjectProcessor(AttachStorageManager attachStorageManager) {
        this.attachStorageManager = attachStorageManager;
    }

    @Override
    public Object postProcessBeforeInitialization(@NonNull Object bean, @NonNull String beanName) throws BeansException {
        ReflectionUtils.doWithFields(bean.getClass(), field -> {
            AttachStorageInject annotation = field.getAnnotation(AttachStorageInject.class);
            if (annotation == null) {
                return;
            }
            String id = annotation.value();
            String name = annotation.name().isEmpty() ? id : annotation.name();
            String description = annotation.description();

            attachStorageManager.registerStorageDomain(
                    AttachStorageDomainDefinition.builder()
                            .id(id)
                            .name(name)
                            .description(description)
                            .build()
            );

            field.setAccessible(true);
            field.set(bean, attachStorageManager.getStorage(id));
        }, field -> field.getAnnotation(AttachStorageInject.class) != null);
        return bean;
    }
}
