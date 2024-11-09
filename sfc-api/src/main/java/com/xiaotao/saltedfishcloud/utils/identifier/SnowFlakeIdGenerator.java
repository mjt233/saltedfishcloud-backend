package com.xiaotao.saltedfishcloud.utils.identifier;

import com.xiaotao.saltedfishcloud.model.template.BaseModel;
import com.xiaotao.saltedfishcloud.utils.ClassUtils;
import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.IdentifierGenerator;
import org.springframework.security.util.FieldUtils;

import jakarta.persistence.Id;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SnowFlakeIdGenerator implements IdentifierGenerator {
    private final Map<Class<?>, String> classIdCache = new ConcurrentHashMap<>();
    @Override
    public Serializable generate(SharedSessionContractImplementor sharedSessionContractImplementor, Object o) {
        Serializable originId;
        if (o instanceof BaseModel) {
            originId = ((BaseModel) o).getId();
        } else {
            String id = classIdCache.get(o.getClass());
            if (id == null) {
                id = ClassUtils.getAllFields(o.getClass())
                        .stream()
                        .filter(e -> e.getAnnotation(Id.class) != null)
                        .findAny()
                        .map(Field::getName)
                        .orElse("id");
                classIdCache.put(o.getClass(), id);
            }
            originId = (Serializable) FieldUtils.getProtectedFieldValue(id, o);
        }
        if (originId != null) {
            return originId;
        }
        return IdUtil.getId();
    }
}
