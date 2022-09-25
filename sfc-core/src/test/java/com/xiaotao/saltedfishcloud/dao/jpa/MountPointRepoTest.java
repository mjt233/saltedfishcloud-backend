package com.xiaotao.saltedfishcloud.dao.jpa;

import com.xiaotao.saltedfishcloud.SaltedfishcloudApplication;
import com.xiaotao.saltedfishcloud.model.po.MountPoint;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.jupiter.api.Assertions.*;


@SpringBootTest(classes = SaltedfishcloudApplication.class)
class MountPointRepoTest {
    @Autowired
    private MountPointRepo mountPointRepo;

    @Test
    public void testInsert() {
        MountPoint mountPoint = MountPoint.builder()
                .nid("0")
                .uid(0L)
                .params("{}")
                .protocol("test")
                .build();
        mountPointRepo.save(mountPoint);
    }
}