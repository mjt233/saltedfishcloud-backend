package com.xiaotao.saltedfishcloud;

import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.service.FileService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest()
public class T2 {
    @Autowired
    FileService fileService;

    @Test
    public void t() {
        System.out.println(DiskConfig.PUBLIC_ROOT);
//        fileService.deepScanDir(DiskConfig.);
    }
}
