package com.saltedfishcloud.ext.sftp;

import com.saltedfishcloud.ext.sftp.config.SFTPProperty;
import com.xiaotao.saltedfishcloud.utils.ObjectUtils;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

public class PropertyTest {
    @Test
    public void doTest() {

        SFTPProperty sftpProperty = new SFTPProperty();
        Map<String, Object> params = new HashMap<>();
        params.put("host", "127.0.0.1");
        ObjectUtils.copyMapToBean(params, sftpProperty);
        System.out.println(sftpProperty);
    }
}
