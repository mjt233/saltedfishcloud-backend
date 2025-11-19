package com.xiaotao.saltedfishcloud.utils.identifier;

import com.xiaotao.saltedfishcloud.model.template.BaseModel;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.IdentifierGenerator;

import java.io.Serializable;

public class SnowFlakeIdGenerator implements IdentifierGenerator {
    @Override
    public Serializable generate(SharedSessionContractImplementor sharedSessionContractImplementor, Object o) {
        if (o instanceof BaseModel model) {
            if (model.getId() == null) {
                return IdUtil.getId();
            } else {
                return model.getId();
            }
        }
        return IdUtil.getId();
    }
}
