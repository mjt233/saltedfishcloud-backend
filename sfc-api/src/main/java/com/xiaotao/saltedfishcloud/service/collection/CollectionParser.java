package com.xiaotao.saltedfishcloud.service.collection;

import com.fasterxml.jackson.core.type.TypeReference;
import com.xiaotao.saltedfishcloud.model.dto.SubmitFile;
import com.xiaotao.saltedfishcloud.model.po.CollectionField;
import com.xiaotao.saltedfishcloud.model.po.CollectionInfo;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import com.xiaotao.saltedfishcloud.validator.FileNameValidator;

import java.util.List;
import java.util.Map;

public class CollectionParser {
    public static final TypeReference<List<CollectionField>> FIELD_LIST_TYPE_REFERENCE =  new TypeReference<List<CollectionField>>() {};

    /**
     * 解析文件收集的字段变量，构造目标文件名。若文件收集不存在字段，则直接返回原文件名
     * @param ci    文件收集信息
     * @param sf    文件提交信息
     * @return      文件名
     */
    public static String parseFilename(CollectionInfo ci, SubmitFile sf) {

        // 无字段直接返回原文
        List<CollectionField> fields = ci.getFields();
        String filename = sf.getFileParam().getName();
        if (fields == null) return filename;

        // 替换字段变量
        String pattern = ci.getPattern();
        Map<String, String> fieldMap = sf.getFieldMap();
        for (CollectionField field : fields) {
            pattern = pattern.replaceAll("\\$\\{" + field.getName() + "}", fieldMap.get(field.getName()));
        }
        String name = pattern.replaceAll("\\$\\{__ext__}", FileUtils.getSuffix(filename));

        if(!FileNameValidator.valid(pattern)) {
            throw new IllegalArgumentException("文件名不合法！" + name);
        }
        return name;
    }
}
