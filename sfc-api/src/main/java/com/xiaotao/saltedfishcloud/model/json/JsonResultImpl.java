package com.xiaotao.saltedfishcloud.model.json;

import com.xiaotao.saltedfishcloud.utils.TypeUtils;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class JsonResultImpl<T> extends AbstractJsonResult<T> {
    private static final long serialVersionUID = 1537580038140716422L;
    private final Map<String, Object> map = new HashMap<>();

    public JsonResultImpl() {
        this(200, null, "OK");
    }

    public JsonResultImpl(int code, T data, String msg) {
        this.put("code", code);
        this.put("data", data);
        this.put("msg", msg);
    }
    public JsonResultImpl(int code, int businessCode, T data, String msg) {
        this.put("code", code);
        this.put("data", data);
        this.put("msg", msg);
        this.put("businessCode", businessCode);
    }

    @Override
    public int getBusinessCode() {
        return TypeUtils.toInt(this.map.getOrDefault("businessCode", 200));
    }

    public JsonResultImpl<T> put(String key, Object obj) {
        map.put(key, obj);
        return this;
    }

    public static <T> JsonResultImpl<T> getInstance(int code, T data, String msg) {
        return new JsonResultImpl<>(code, data, msg);
    }
    public static <T> JsonResultImpl<T> getInstance(int code, int businessCode, T data, String msg) {
        return new JsonResultImpl<>(code, businessCode ,data, msg);
    }
    public static <T> JsonResultImpl<T> getInstance(T data) {
        return getInstance(200, data, "OK");
    }

    /**
     * 获取一个数据Map实例
     */
    public static LinkedHashMap<String, Object> getDataMap() {
        return new LinkedHashMap<>();
    }

    @Override
    public int getCode() {
        return (int)map.get("code");
    }

    public JsonResultImpl<T> setCode(int code) {
        this.put("code", code);
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T getData() {
        return (T)map.get("data");
    }

    public JsonResultImpl<T> setData(Object data) {
        this.put("data", data);
        return this;
    }

    @Override
    public String getMsg() {
        return (String)map.get("msg");
    }

    public JsonResultImpl<T> setMsg(String msg) {
        this.put("msg", msg);
        return this;
    }

    @Override
    public String toString() {
        return getJsonStr();
    }

}
