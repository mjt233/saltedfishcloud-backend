package com.xiaotao.saltedfishcloud.dao.jpa;

import com.xiaotao.saltedfishcloud.entity.po.DownloadTaskInfo;
import lombok.var;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

import javax.annotation.Resource;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class DownloadTaskRepositoryTest {
    @Resource
    private DownloadTaskRepo downloadTaskRepo;

    @Test
    public void testMethod() {
        var res = downloadTaskRepo.findByUidOrderByCreatedAtDesc(1, PageRequest.of(0, 10));
        for (DownloadTaskInfo info : res) {
            System.out.println(info);
        }
    }

    @Test
    public void doTest() {
        var info = new DownloadTaskInfo();
        info.setProxy("local");
        info.setUid(1);
        info.setUrl("abc");
        downloadTaskRepo.saveAndFlush(info);
        System.out.println(info.getId());
        info.setUrl("2333");
        downloadTaskRepo.saveAndFlush(info);
        var dbInfo = downloadTaskRepo.findById(info.getId()).get();
        assertEquals("2333", dbInfo.getUrl());
        System.out.println(dbInfo.getCreatedAt());
    }
}
