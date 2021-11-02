package com.xiaotao.saltedfishcloud.service.collection;

import com.xiaotao.saltedfishcloud.dao.mybatis.UserDao;
import com.xiaotao.saltedfishcloud.entity.dto.CollectionDTO;
import com.xiaotao.saltedfishcloud.entity.dto.SubmitFile;
import com.xiaotao.saltedfishcloud.entity.po.NodeInfo;
import com.xiaotao.saltedfishcloud.entity.po.User;
import com.xiaotao.saltedfishcloud.entity.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.filesystem.DiskFileSystem;
import com.xiaotao.saltedfishcloud.service.file.filesystem.DiskFileSystemFactory;
import com.xiaotao.saltedfishcloud.service.node.NodeService;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import org.springframework.util.ResourceUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.LinkedList;

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
        String cid = cs.createCollection(u.getId(), colI);

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

        // 保存文件到收集任务
        cs.collectFile(cid, u.getId(), resource.getInputStream(), fileInfo, file);
    }
}
