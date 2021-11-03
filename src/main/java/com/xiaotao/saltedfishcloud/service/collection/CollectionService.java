package com.xiaotao.saltedfishcloud.service.collection;

import com.xiaotao.saltedfishcloud.dao.jpa.CollectionInfoRepository;
import com.xiaotao.saltedfishcloud.dao.jpa.CollectionRecordRepo;
import com.xiaotao.saltedfishcloud.entity.ErrorInfo;
import com.xiaotao.saltedfishcloud.entity.dto.CollectionDTO;
import com.xiaotao.saltedfishcloud.entity.dto.SubmitFile;
import com.xiaotao.saltedfishcloud.entity.po.CollectionInfo;
import com.xiaotao.saltedfishcloud.entity.po.CollectionRecord;
import com.xiaotao.saltedfishcloud.entity.po.CollectionRecordId;
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
    private final CollectionRecordRepo recordDao;
    private final NodeService nodeService;
    private final DiskFileSystemFactory fileSystem;

    /**
     * 创建收集任务
     * @param uid   创建者ID
     * @param info  收集任务信息
     * @return      收集任务ID
     */
    public String createCollection(int uid, CollectionDTO info) {
        if(!CollectionValidator.validateCreate(info)) {
            throw new JsonException(ErrorInfo.COLLECTION_CHECK_FAILED);
        }
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

    /**
     * 接受一个文件存到集合中
     * @param cid   收集ID
     * @param uid   提供者ID，游客使用0
     * @param is    文件输入流
     * @param fileInfo      基础文件信息（至少应包含文件名，大小，md5）
     * @param submitFile    提交的文件信息
     */
    @Transactional(rollbackFor = Throwable.class)
    public void collectFile(String cid, int uid, InputStream is, FileInfo fileInfo, SubmitFile submitFile) throws IOException {
        CollectionInfo ci = collectionDao.findById(cid).orElse(null);
        // 校验收集存在
        if (ci == null) { throw new JsonException(ErrorInfo.COLLECTION_NOT_FOUND); }

        // 校验开关状态
        if (ci.getState() == CollectionInfo.State.CLOSED) {
            throw new JsonException(ErrorInfo.COLLECTION_CLOSED);
        }

        // 校验匿名状态
        if (!ci.getAllowAnonymous() && uid == 0) { throw new JsonException(ErrorInfo.COLLECTION_REQUIRE_LOGIN); }

        // 校验约束
        if (!CollectionValidator.validateSubmit(ci, submitFile)) { throw new JsonException(ErrorInfo.COLLECTION_CHECK_FAILED); }

        // 校验收集数
        Integer allowMax = ci.getAllowMax();
        if (allowMax != null && allowMax > -1) {
            // 收集满
            if (ci.getAvailable() == 0) {
                throw new JsonException(ErrorInfo.COLLECTION_FULL);
            }

            int res = collectionDao.consumeCount(cid, ci.getAvailable());

            // 乐观锁操作失败
            if (res == 0) {
                throw new JsonException(ErrorInfo.SYSTEM_BUSY);
            }
            collectionDao.save(ci);
        }

        if (fileInfo.getMd5() == null) fileInfo.updateMd5();

        String filename = CollectionParser.parseFilename(ci, submitFile);
        CollectionRecord record = new CollectionRecord(new CollectionRecordId(cid, uid), filename, submitFile.getSize(), fileInfo.getMd5());
        recordDao.save(record);
        fileInfo.setName(filename);
        // 存入文件
        String path = nodeService.getPathByNode(ci.getUid(), ci.getSaveNode());
        fileSystem.getFileSystem().saveFile(ci.getUid(), is, path, fileInfo);
    }
}
