package com.xiaotao.saltedfishcloud.dao.jpa;

import com.xiaotao.saltedfishcloud.po.DownloadTaskInfo;
import lombok.var;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

import javax.annotation.Resource;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class DownloadTaskRepositoryTest {
    @Resource
    private DownloadTaskRepository downloadTaskRepository;

    @Test
    public void testMethod() {
        var res = downloadTaskRepository.findByUidOrderByCreatedAtDesc(1, PageRequest.of(0, 10));
        for (DownloadTaskInfo info : res) {
            System.out.println(info);
        }
    }

    @Test
    public void doTest() {
        var info = new DownloadTaskInfo();
        info.proxy = "local";
        info.uid = 1;
        info.url = "abc";
        downloadTaskRepository.saveAndFlush(info);
        System.out.println(info.id);
        info.url = "2333";
        downloadTaskRepository.saveAndFlush(info);
        var dbInfo = downloadTaskRepository.findById(info.id).get();
        assertEquals("2333", dbInfo.url);
        System.out.println(dbInfo.createdAt);
    }
}
