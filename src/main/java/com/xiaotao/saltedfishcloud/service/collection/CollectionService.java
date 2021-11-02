package com.xiaotao.saltedfishcloud.service.collection;

import com.xiaotao.saltedfishcloud.dao.jpa.CollectionInfoRepository;
import com.xiaotao.saltedfishcloud.entity.ErrorInfo;
import com.xiaotao.saltedfishcloud.entity.dto.CollectionDTO;
import com.xiaotao.saltedfishcloud.entity.dto.SubmitFile;
import com.xiaotao.saltedfishcloud.entity.po.CollectionInfo;
import com.xiaotao.saltedfishcloud.entity.po.NodeInfo;
import com.xiaotao.saltedfishcloud.entity.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.service.file.filesystem.DiskFileSystemFactory;
import com.xiaotao.saltedfishcloud.service.node.NodeService;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;


@Service
@RequiredArgsConstructor
public class CollectionService {
    private final CollectionInfoRepository collectionDao;
    private final NodeService nodeService;
    private final DiskFileSystemFactory fileSystem;

    /**
     * 创建收集任务
     * @param uid   创建者ID
     * @param info  收集任务信息
     * @return      收集任务ID
     */
    public String createCollection(int uid, CollectionDTO info) {
        NodeInfo node = nodeService.getNodeById(uid, info.getSaveNode());
        if (node == null) {
            throw new JsonException(ErrorInfo.NODE_NOT_FOUND);
        }
        CollectionInfo ci = new CollectionInfo(uid, info);
        ci.setId(SecureUtils.getUUID());
        collectionDao.save(ci);
        return ci.getId();
    }

    /**
     * 获取一个收集信息
     * @param cid   收集ID
     * @return      收集信息，若收集id不存在，则返回null
     */
    public CollectionInfo getCollection(String cid) {
        Optional<CollectionInfo> r = collectionDao.findById(cid);
        return r.orElse(null);
    }

    @Transactional(rollbackFor = Throwable.class)
    public void collectFile(String cid, int uid, InputStream is, FileInfo fileInfo, SubmitFile submitFile) throws IOException {
        CollectionInfo ci = collectionDao.findById(cid).orElse(null);
        // 校验收集存在
        if (ci == null) { throw new JsonException(ErrorInfo.COLLECTION_NOT_FOUND); }

        // 校验匿名状态
        if (!ci.getAllowAnonymous() && uid == 0) { throw new JsonException(ErrorInfo.COLLECTION_REQUIRE_LOGIN); }

        // 校验约束
        if (!CollectionValidator.validateSubmit(ci, submitFile)) { throw new JsonException(ErrorInfo.COLLECTION_CHECK_FAILED); }

        // 存入文件
        String path = nodeService.getPathByNode(ci.getUid(), ci.getSaveNode());
        fileSystem.getFileSystem().saveFile(ci.getUid(), is, path, fileInfo);
    }
}
