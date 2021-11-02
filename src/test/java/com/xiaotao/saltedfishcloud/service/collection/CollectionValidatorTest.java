package com.xiaotao.saltedfishcloud.service.collection;

import com.xiaotao.saltedfishcloud.entity.dto.CollectionDTO;
import com.xiaotao.saltedfishcloud.entity.dto.SimpleField;
import com.xiaotao.saltedfishcloud.entity.dto.SubmitFile;
import com.xiaotao.saltedfishcloud.entity.po.CollectionField;
import com.xiaotao.saltedfishcloud.entity.po.CollectionInfo;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
class CollectionValidatorTest {

    @Test
    void validateCreate() {
        CollectionDTO col = new CollectionDTO("123", "1", new Date(), "233");
        col.setPattern("\\[.$?");
        assertTrue(CollectionValidator.validateCreate(col));
        LinkedList<CollectionField> fields = new LinkedList<>();
        fields.add(new CollectionField("name", CollectionField.Type.TEXT));

        // 使用字段但表达式未使用字段变量，校验不通过
        col.setField(fields);
        assertFalse(CollectionValidator.validateCreate(col));

        // 使用字段变量，校验通过
        col.setPattern("${name}.doc");
        assertTrue(CollectionValidator.validateCreate(col));

        // 字段使用保留关键字变量，校验不通过
        fields.add(new CollectionField("__ext__", CollectionField.Type.TEXT));
        assertFalse(CollectionValidator.validateCreate(col));

        // 移除非法字段，校验通过
        fields.removeLast();
        assertTrue(CollectionValidator.validateCreate(col));

        // 使用字段但不使用字段表达式，校验不通过
        col.setPattern(null);
        assertFalse(CollectionValidator.validateCreate(col));

        col.setField(null);
    }

    @Test
    void validateSubmit() {
        CollectionInfo info = new CollectionInfo(0, "测试", "测试", "测试", "测试", new Date(), "doc");
        SubmitFile submitFile = new SubmitFile("test.doc", 1024L, null);

        // 无约束
        assertTrue(CollectionValidator.validateSubmit(info, submitFile));

        // 文件名不匹配正则
        info.setPattern("233$");
        assertFalse(CollectionValidator.validateSubmit(info, submitFile));

        // 匹配正则
        info.setPattern("doc$");
        assertTrue(CollectionValidator.validateSubmit(info, submitFile));


        // 添加字段约束
        List<CollectionField> fields = new LinkedList<>();
        fields.add(new CollectionField("name", CollectionField.Type.TEXT){{setPattern("二$");}});
        fields.add(new CollectionField("age", CollectionField.Type.TEXT){{setPattern("^2[0-9]$");}});
        info.setField(fields);
        info.setPattern("${name}-${age}.${__ext__}");
        info.setExtPattern("(doc|docx)$");

        // 字段不匹配约束
        assertFalse(CollectionValidator.validateSubmit(info, submitFile));


        LinkedList<SimpleField> submitFields = new LinkedList<>();
        submitFields.add(new SimpleField("name", "田所浩二"));
        submitFields.add(new SimpleField("age", "24"));
        submitFile.setField(submitFields);
        submitFile.setFilename("koko.doc");

        // 符合约束
        assertTrue(CollectionValidator.validateSubmit(info, submitFile));


        // 字段不符合约束
        submitFields.getFirst().setValue("田所浩三");
        assertFalse(CollectionValidator.validateSubmit(info, submitFile));

        // 取消字段正则约束(null)
        fields.get(0).setPattern(null);
        info.setField(fields);
        assertTrue(CollectionValidator.validateSubmit(info, submitFile));

        // 取消字段正则约束(空字符串)
        fields.get(0).setPattern("");
        info.setField(fields);
        assertTrue(CollectionValidator.validateSubmit(info, submitFile));

        // 缺少字段
        submitFields.removeLast();
        assertFalse(CollectionValidator.validateSubmit(info, submitFile));
        submitFields.add(new SimpleField("age", "24"));

        // 文件拓展名不符合
        submitFile.setFilename("koko.pdf");
        assertFalse(CollectionValidator.validateSubmit(info, submitFile));

        // 文件过大
        submitFile.setFilename("koko.doc");
        submitFile.setSize(114514L);
        info.setMaxSize(1024L);
        assertFalse(CollectionValidator.validateSubmit(info, submitFile));

        // 取消限制
        info.setMaxSize(-1L);
        assertTrue(CollectionValidator.validateSubmit(info, submitFile));
    }
}
