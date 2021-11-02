package com.xiaotao.saltedfishcloud.dao.jpa;

import com.xiaotao.saltedfishcloud.entity.po.CollectionField;
import com.xiaotao.saltedfishcloud.entity.po.CollectionInfo;
import com.xiaotao.saltedfishcloud.utils.ByteSize;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class CollectionInfoRepositoryTest {
    @Autowired
    private CollectionInfoRepository repository;

    @Test
    public void testAdd() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.HOUR, 24);
        CollectionInfo info = new CollectionInfo(2, "admin", "测试收集", "啦啦啦", "root", calendar.getTime(), null);
        info.setMaxSize(ByteSize._1MiB * 512L);
        repository.save(info);
        assertTrue(repository.findAll().size() > 0);

        Collection<CollectionField> fields = new ArrayList<>();
        fields.add(new CollectionField()
                .setName("姓名").setDescribe("你的姓名").setType(CollectionField.Type.TEXT));

        fields.add(new CollectionField()
                .setName("班级").setType(CollectionField.Type.OPTION).addOption("1班").addOption("2班").addOption("3班"));

        info.setField(fields);
        repository.save(info);
        System.out.println(info.getId());
    }
}
