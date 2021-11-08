package com.xiaotao.saltedfishcloud.service.collection;

import com.xiaotao.saltedfishcloud.dao.mybatis.UserDao;
import com.xiaotao.saltedfishcloud.entity.dto.CollectionDTO;
import com.xiaotao.saltedfishcloud.entity.dto.SubmitFile;
import com.xiaotao.saltedfishcloud.entity.po.CollectionInfoId;
import com.xiaotao.saltedfishcloud.entity.po.CollectionRecord;
import com.xiaotao.saltedfishcloud.entity.po.NodeInfo;
import com.xiaotao.saltedfishcloud.entity.po.User;
import com.xiaotao.saltedfishcloud.entity.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.service.file.filesystem.DiskFileSystemFactory;
import com.xiaotao.saltedfishcloud.service.node.NodeService;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Page;
import org.springframework.util.DigestUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class CollectionServiceTest {
    @Autowired
    private CollectionService cs;
    @Autowired
    private UserDao userDao;
    @Autowired
    private NodeService nodeService;
    @Autowired
    private DiskFileSystemFactory diskFileSystem;

    @Test
    void testGet() {
        CollectionDTO nodeInfo = new CollectionDTO("t", StringUtils.getRandomString(32), new Date(), "adminTest");
        User admin = userDao.getUserByUser("admin");
        try {
            cs.createCollection(admin.getId(), nodeInfo);
            fail();
        } catch (JsonException e) {
            System.out.println(e.toString());
        }
        nodeInfo.setSaveNode("" + admin.getId());
        CollectionInfoId cid = cs.createCollection(admin.getId(), nodeInfo);
        assertNotNull(cs.getCollectionWitchVerification(cid));
    }

    @Test
    void collectFile() throws IOException {
        User u = userDao.getUserByUser("admin");
        String title = "测试收集样例";
        String savePath = "/我的收集" + "/" + title;

        diskFileSystem.getFileSystem().mkdirs(u.getId(), savePath);
        Calendar calender = Calendar.getInstance();
        calender.add(Calendar.DATE, 7);

        NodeInfo node = nodeService.getLastNodeInfoByPath(u.getId(), savePath);
        // 创建收集任务
        CollectionDTO colI = new CollectionDTO("测试收集样例", node.getId(), calender.getTime(), u.getUsername());
        colI.setPattern("\\.(doc|docx)$");
        CollectionInfoId cid = cs.createCollection(u.getId(), colI);

        ClassPathResource resource = new ClassPathResource("/sql/full.sql");

        // 准备文件信息
        InputStream is = resource.getInputStream();
        String md5 = DigestUtils.md5DigestAsHex(is);

        FileInfo fileInfo = new FileInfo();
        fileInfo.setName("full.sql");
        fileInfo.setMd5(md5);
        fileInfo.setSize(resource.contentLength());
        fileInfo.updateMd5();


        SubmitFile file = new SubmitFile("full.sql", resource.contentLength(), null);

        String ip = "127.0.0.1";
        // 保存文件到收集任务
        try {
            // 文件名不匹配正则
            cs.collectFile(cid, u.getId(), resource.getInputStream(), fileInfo, file, ip);
            fail();
        } catch (JsonException e) {
            System.out.println(e.toString());
        }
        file.setFilename("full.docx");

        // OK
        cs.collectFile(cid, u.getId(), resource.getInputStream(), fileInfo, file, ip);
    }

    @Test
    void getSubmits() {
        int page = 0, size = 2;
        Page<CollectionRecord> submits = cs.getSubmits(5L, page, size);
        while (submits.getNumberOfElements() > 0) {
            for (CollectionRecord record : submits.getContent()) {
                System.out.println(record);
            }
            submits = cs.getSubmits(5L, ++page, size);
        }
    }
}
