package com.xiaotao.saltedfishcloud.service.collection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.xiaotao.saltedfishcloud.entity.ErrorInfo;
import com.xiaotao.saltedfishcloud.entity.dto.CollectionDTO;
import com.xiaotao.saltedfishcloud.entity.dto.SubmitFile;
import com.xiaotao.saltedfishcloud.entity.po.CollectionField;
import com.xiaotao.saltedfishcloud.entity.po.CollectionInfo;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import com.xiaotao.saltedfishcloud.validator.FileNameValidator;
import org.springframework.util.StringUtils;
import static com.xiaotao.saltedfishcloud.utils.StringUtils.*;

import java.util.List;

/**
 * 文件收集信息校验器，用于校验创建的收集任务或提交的文件是否符合对应约束
 */
public class CollectionValidator {
    private static final TypeReference<List<CollectionField>> FIELD_LIST_TYPE_REFERENCE =  new TypeReference<List<CollectionField>>() {};
    /**
     * 校验创建的任务是否符合约束<br>
     * 存在field时，pattern不可为空
     * @param collectionDTO 收集任务创建信息
     */
    public static boolean validateCreate(CollectionDTO collectionDTO) {
        String pattern = collectionDTO.getPattern();

        List<CollectionField> field = collectionDTO.getField();

        // 当使用字段时，确保表达式有使用字段变量且变量名不与内置变量名冲突
        if (field != null && field.size() > 0) {
            if (!StringUtils.hasText(pattern)) return false;
            int originLen = pattern.length();
            for (CollectionField f : field) {
                if ("__ext__".equals(f.getName())) return false;
                pattern = pattern.replaceAll("\\$\\{" + f.getName() + "}", "");
            }
            if (originLen == pattern.length()) {
                return false;
            }
            String extPattern = collectionDTO.getExtPattern();
            return extPattern == null || StringUtils.hasText(extPattern);
        }
        return true;
    }
    /**
     * 验证给定的提交文件信息是否符合收集任务的约束条件
     * @param info          收集任务信息
     * @param submitFile    提交文件信息
     */
    public static boolean validateSubmit(CollectionInfo info, SubmitFile submitFile) {
        // 校验非法文件名
        String n = submitFile.getFilename();
        if (!FileNameValidator.valid(n)) { return false; }

        // 校验文件大小
        if (info.getMaxSize() > -1 && info.getMaxSize() < submitFile.getSize()) { return false; }

        String rawField = info.getField();
        if (StringUtils.hasLength(rawField)) {
            return validateField(info, submitFile);
        } else if (StringUtils.hasLength(info.getPattern())){
            return validatePattern(info, submitFile);
        } else {
            return true;
        }
    }

    /**
     * 验证提交的文件是否符合表达式
     */
    private static boolean validatePattern(CollectionInfo info, SubmitFile submitFile) {
        String pattern = info.getPattern();
        return matchRegex(pattern, submitFile.getFilename());
    }

    /**
     * 验证提交的文件是否符合表达式
     */
    private static boolean validateField(CollectionInfo info, SubmitFile submitFile) {
        try {
            String name = submitFile.getFilename();

            // 校验拓展名
            String ext = FileUtils.getSuffix(name);
            String extP = info.getExtPattern();
            if (extP != null && !matchRegex(extP, ext)) {
                return false;
            }


            // 逐字段校验
            List<CollectionField> fields = MapperHolder.mapper.readValue(info.getField(), FIELD_LIST_TYPE_REFERENCE);
            for (CollectionField field : fields) {
                if(StringUtils.hasLength(field.getPattern())) {
                    if (!matchRegex(field.getPattern(), submitFile.getFieldMap().get(field.getName()))) {
                        return false;
                    }
                }
            }
        } catch (JsonProcessingException ignore) { }
        return true;
    }


}
