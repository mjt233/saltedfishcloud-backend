package com.xiaotao.saltedfishcloud.utils.identifier;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.generator.EventType;
import org.hibernate.generator.GeneratorCreationContext;
import org.hibernate.id.factory.spi.CustomIdGeneratorCreationContext;
import org.hibernate.id.uuid.UuidGenerator;

import java.lang.reflect.Member;

/**
 * 不带横杠“-”的系统UUID生成器，基于JPA默认的UUID生成器去掉横杠
 */
public class SystemUuidGenerator extends UuidGenerator {

    @org.hibernate.annotations.UuidGenerator
    private static org.hibernate.annotations.UuidGenerator UUID_GENERATOR_ANNOTATIONS;
    static {
        try {
            UUID_GENERATOR_ANNOTATIONS = SystemUuidGenerator.class.getDeclaredField("UUID_GENERATOR_ANNOTATIONS").getAnnotation(org.hibernate.annotations.UuidGenerator.class);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
    }

    public SystemUuidGenerator(com.xiaotao.saltedfishcloud.annotations.id.SystemUuidGenerator config, Member idMember, CustomIdGeneratorCreationContext creationContext) {
        super(UUID_GENERATOR_ANNOTATIONS, idMember, creationContext);
    }

    public SystemUuidGenerator(com.xiaotao.saltedfishcloud.annotations.id.SystemUuidGenerator config, Member member, GeneratorCreationContext creationContext) {
        super(UUID_GENERATOR_ANNOTATIONS, member, creationContext);
    }

    @Override
    public Object generate(SharedSessionContractImplementor session, Object owner, Object currentValue, EventType eventType) {
        Object result = super.generate(session, owner, currentValue, eventType);
        if (result instanceof String) {
            return ((String) result).replaceAll("-", "");
        } else {
            return result;
        }
    }
}
