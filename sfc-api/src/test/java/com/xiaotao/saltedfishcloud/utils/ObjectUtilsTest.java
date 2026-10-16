package com.xiaotao.saltedfishcloud.utils;

import com.sfc.cloud.data.utils.SfcClassUtils;
import com.xiaotao.saltedfishcloud.model.po.ProxyInfo;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ObjectUtilsTest {

    @Test
    void getEntityTableName() {
        assertEquals("file_table", SfcClassUtils.getEntityTableName(FileInfo.class));
    }

    @Test
    void getClassEntityFieldGetter() {
        List<SfcClassUtils.ColumnGetter> list = SfcClassUtils.getClassEntityFieldGetter(ProxyInfo.class);
        assertEquals(9, list.size());

        list = SfcClassUtils.getClassEntityFieldGetter(FileInfo.class);
        assertEquals(11, list.size());
    }
}
