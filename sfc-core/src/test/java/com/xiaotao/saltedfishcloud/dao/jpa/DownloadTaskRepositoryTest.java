package com.xiaotao.saltedfishcloud.dao.jpa;

import com.xiaotao.saltedfishcloud.download.model.DownloadTaskInfo;
import com.xiaotao.saltedfishcloud.download.repo.DownloadTaskRepo;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import javax.annotation.Resource;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class DownloadTaskRepositoryTest {
    @Resource
    private DownloadTaskRepo downloadTaskRepo;

    @Test
    public void testMethod() {
        Page<DownloadTaskInfo> res = downloadTaskRepo.findByUid(1L, PageRequest.of(0, 10));
        for (DownloadTaskInfo info : res) {
            System.out.println(info);
        }
    }

    @Test
    public void doTest() {
        DownloadTaskInfo info = new DownloadTaskInfo();
        info.setProxy("local");
        info.setUid(1);
        info.setUrl("abc");
        downloadTaskRepo.saveAndFlush(info);
        System.out.println(info.getId());
        info.setUrl("2333");
        downloadTaskRepo.saveAndFlush(info);
        DownloadTaskInfo dbInfo = downloadTaskRepo.findById(info.getId()).get();
        assertEquals("2333", dbInfo.getUrl());
        System.out.println(dbInfo.getCreatedAt());
    }
}
