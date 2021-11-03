package com.xiaotao.saltedfishcloud.entity.dto;

import lombok.*;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@NoArgsConstructor
@Data
public class SubmitFile {
    @NotBlank
    private String filename;
    private Long size;
    private List<SimpleField> field;


    public SubmitFile(String filename, Long size, List<SimpleField> field) {
        this.filename = filename;
        this.size = size;
        this.field = field;
    }

    /**
     * 获取以字段名为key，字段值为value的map<br>
     * 每次调用获取到的都是新的Map对象，对该对象的修改不会影响原始数据
     */
    public Map<String, String> getFieldMap() {
        Map<String,String> map = new HashMap<>();
        if (field != null) {
            for (SimpleField simpleField : field) {
                map.put(simpleField.getName(), simpleField.getValue());
            }
        }
        return map;
    }
}
