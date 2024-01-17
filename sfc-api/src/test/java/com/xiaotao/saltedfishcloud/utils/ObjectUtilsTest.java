package com.xiaotao.saltedfishcloud.utils;

import com.xiaotao.saltedfishcloud.model.po.ProxyInfo;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import org.junit.jupiter.api.Test;
import reactor.util.function.Tuple2;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ObjectUtilsTest {

    @Test
    void getEntityTableName() {
        assertEquals("file_table", ObjectUtils.getEntityTableName(FileInfo.class));
    }

    @Test
    void getClassEntityFieldGetter() {
        List<Tuple2<String, Method>> list = ObjectUtils.getClassEntityFieldGetter(ProxyInfo.class);
        assertEquals(9, list.size());

        list = ObjectUtils.getClassEntityFieldGetter(FileInfo.class);
        assertEquals(11, list.size());
    }
}