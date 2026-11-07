package com.xiaotao.saltedfishcloud.utils;

import com.xiaotao.saltedfishcloud.constant.MQTopicConstants;
import com.xiaotao.saltedfishcloud.model.NameValueType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.sfc.common.utils.SfcClassUtils;

class ClassUtilsTest {

    @Test
    void getTypeArguments() {
        assertEquals(NameValueType.class, SfcClassUtils.getTypeParameterBySuperClass(MQTopicConstants.CONFIG_CHANGE));
    }
}