package com.xiaotao.saltedfishcloud.model.dto;

import com.xiaotao.saltedfishcloud.model.param.FileInfoSaveParam;
import lombok.*;

import jakarta.validation.constraints.NotBlank;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@NoArgsConstructor
@Data
public class SubmitFile {

    /**
     * 文件保存参数
     */
    private FileInfoSaveParam fileParam;

    /**
     * 填写的字段信息
     */
    private List<SimpleField> field;


    public SubmitFile(String filename, Long size, List<SimpleField> field) {
        this.fileParam = FileInfoSaveParam.builder()
                .name(filename)
                .size(size)
                .build();
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
