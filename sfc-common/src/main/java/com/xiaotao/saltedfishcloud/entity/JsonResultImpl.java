package com.xiaotao.saltedfishcloud.entity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;

/**
 * @TODO 修改为可迭代的接口或抽象类，并编写一个默认的实现类和一个空数据的只读单例实现类
 */
public class JsonResultImpl extends LinkedHashMap<String, Object> implements JsonResult {
    private static final long serialVersionUID = 1537580038140716422L;

    public JsonResultImpl() {
        this(200, null, "OK");
    }

    public JsonResultImpl(int code, Object data, String msg) {
        this.put("code", code);
        this.put("data", data);
        this.put("msg", msg);
    }

    @Override
    public JsonResultImpl put(String key, Object obj) {
        super.put(key, obj);
        return this;
    }

    public static JsonResultImpl getInstance(int code, Object data, String msg) {
        return new JsonResultImpl(code, data, msg);
    }
    public static JsonResultImpl getInstance(Object data) {
        return getInstance(200, data, "OK");
    }
    public static JsonResultImpl getInstance() {
        return getInstance(200, null, "OK");
    }

    /**
     * 获取一个数据Map实例
     */
    public static LinkedHashMap<String, Object> getDataMap() {
        return new LinkedHashMap<>();
    }

    @Override
    public int getCode() {
        return (int)this.get("code");
    }

    public JsonResult setCode(int code) {
        this.put("code", code);
        return this;
    }

    @Override
    public Object getData() {
        return this.get("data");
    }

    public JsonResultImpl setData(Object data) {
        this.put("data", data);
        return this;
    }

    @Override
    public String getMsg() {
        return (String)this.get("msg");
    }

    public JsonResultImpl setMsg(String msg) {
        this.put("msg", msg);
        return this;
    }

    @Override
    public String toString() {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
