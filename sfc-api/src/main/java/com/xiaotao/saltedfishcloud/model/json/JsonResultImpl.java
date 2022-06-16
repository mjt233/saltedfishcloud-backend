package com.xiaotao.saltedfishcloud.model.json;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class JsonResultImpl extends AbstractJsonResult {
    private static final long serialVersionUID = 1537580038140716422L;
    private final Map<String, Object> map = new HashMap<>();

    public JsonResultImpl() {
        this(200, null, "OK");
    }

    public JsonResultImpl(int code, Object data, String msg) {
        this.put("code", code);
        this.put("data", data);
        this.put("msg", msg);
    }

    public JsonResultImpl put(String key, Object obj) {
        map.put(key, obj);
        return this;
    }

    public static JsonResultImpl getInstance(int code, Object data, String msg) {
        return new JsonResultImpl(code, data, msg);
    }
    public static JsonResultImpl getInstance(Object data) {
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

    public JsonResultImpl setCode(int code) {
        this.put("code", code);
        return this;
    }

    @Override
    public Object getData() {
        return map.get("data");
    }

    public JsonResultImpl setData(Object data) {
        this.put("data", data);
        return this;
    }

    @Override
    public String getMsg() {
        return (String)map.get("msg");
    }

    public JsonResultImpl setMsg(String msg) {
        this.put("msg", msg);
        return this;
    }

    @Override
    public String toString() {
        return getJsonStr();
    }

}
