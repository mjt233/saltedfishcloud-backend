package com.xiaotao.saltedfishcloud.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;

public abstract class AbstractJsonResult implements JsonResult {
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
