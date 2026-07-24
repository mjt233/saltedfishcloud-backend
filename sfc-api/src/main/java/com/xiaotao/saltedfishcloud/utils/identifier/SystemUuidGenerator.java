package com.xiaotao.saltedfishcloud.utils.identifier;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.generator.AnnotationBasedGenerator;
import org.hibernate.generator.BeforeExecutionGenerator;
import org.hibernate.generator.EventType;
import org.hibernate.generator.EventTypeSets;
import org.hibernate.generator.GeneratorCreationContext;

import java.util.EnumSet;
import java.util.UUID;

/**
 * 不带横杠“-”的系统UUID生成器，生成32位无横杠的随机UUID字符串。
 * <p>
 * 适配 Hibernate 7 的自定义 ID 生成器契约，通过
 * {@link BeforeExecutionGenerator} 与 {@link AnnotationBasedGenerator} 实现，
 * 由 {@code @IdGeneratorType} 元注解驱动实例化与初始化。
 */
public class SystemUuidGenerator
        implements BeforeExecutionGenerator,
        AnnotationBasedGenerator<com.xiaotao.saltedfishcloud.annotations.id.SystemUuidGenerator> {

    /**
     * 仅在插入时生成标识符。
     *
     * @return 插入事件类型集合
     */
    @Override
    public EnumSet<EventType> getEventTypes() {
        return EventTypeSets.INSERT_ONLY;
    }

    /**
     * 生成一个去除横杠的随机UUID字符串。
     *
     * @param session      当前会话
     * @param owner        持有该标识符的实体
     * @param currentValue 当前值（通常为null）
     * @param eventType    触发生成的事件类型
     * @return 32位无横杠的UUID字符串
     */
    @Override
    public Object generate(SharedSessionContractImplementor session, Object owner, Object currentValue, EventType eventType) {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 接收标记注解配置。本生成器无需额外配置参数，故为空实现。
     *
     * @param config         {@code @SystemUuidGenerator} 标记注解
     * @param creationContext 生成器创建上下文
     */
    @Override
    public void initialize(com.xiaotao.saltedfishcloud.annotations.id.SystemUuidGenerator config, GeneratorCreationContext creationContext) {
    }
}
