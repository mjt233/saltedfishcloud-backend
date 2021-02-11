package com.xiaotao.saltedfishcloud.po;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;

public class JsonResult extends LinkedHashMap<String, Object>{
    public JsonResult() {
        this(1, null, "OK");
    }

    public JsonResult(int code, Object data, String msg) {
        this.put("code", code);
        this.put("data", data);
        this.put("msg", msg);
    }

    @Override
    public JsonResult put(String key, Object obj) {
        super.put(key, obj);
        return this;
    }

    public static JsonResult getInstance(int code, Object data, String msg) {
        return new JsonResult(code, data, msg);
    }
    public static JsonResult getInstance(Object data) {
        return getInstance(1, data, "OK");
    }
    public static JsonResult getInstance() {
        return getInstance(1, null, "OK");
    }

    public int getCode() {
        return Integer.parseInt((String)this.get("code"));
    }

    public JsonResult setCode(int code) {
        this.put("code", code);
        return this;
    }

    public Object getData() {
        return this.get("data");
    }

    public JsonResult setData(Object data) {
        this.put("data", data);
        return this;
    }

    public String getMsg() {
        return (String)this.get("msg");
    }

    public JsonResult setMsg(String msg) {
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
