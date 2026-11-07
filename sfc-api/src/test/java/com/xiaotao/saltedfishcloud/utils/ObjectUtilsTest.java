package com.xiaotao.saltedfishcloud.utils;

import com.sfc.common.utils.SfcClassUtils;
import com.sfc.cloud.data.utils.SfcDBUtils;
import com.xiaotao.saltedfishcloud.model.po.ProxyInfo;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ObjectUtilsTest {

    @Test
    void getEntityTableName() {
        assertEquals("file_table", SfcDBUtils.getEntityTableName(FileInfo.class));
    }

    @Test
    void getClassEntityFieldGetter() {
        List<SfcClassUtils.ColumnGetter> list = SfcDBUtils.getClassEntityFieldGetter(ProxyInfo.class);
        assertEquals(9, list.size());

        list = SfcDBUtils.getClassEntityFieldGetter(FileInfo.class);
        assertEquals(11, list.size());
    }
}
