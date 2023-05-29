package com.xiaotao.saltedfishcloud.model.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;

public abstract class AbstractJsonResult<T> implements JsonResult<T> {
    @Override
    public String getJsonStr() {
        try {
            return MapperHolder.mapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return "{}";
        }
    }
}
