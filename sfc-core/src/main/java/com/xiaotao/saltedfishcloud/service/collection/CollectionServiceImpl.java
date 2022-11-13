package com.xiaotao.saltedfishcloud.service.collection;

import com.xiaotao.saltedfishcloud.constant.error.CollectionError;
import com.xiaotao.saltedfishcloud.constant.error.CommonError;
import com.xiaotao.saltedfishcloud.constant.error.FileSystemError;
import com.xiaotao.saltedfishcloud.dao.jpa.CollectionInfoRepo;
import com.xiaotao.saltedfishcloud.dao.jpa.CollectionRecordRepo;
import com.xiaotao.saltedfishcloud.model.dto.CollectionDTO;
import com.xiaotao.saltedfishcloud.model.dto.SubmitFile;
import com.xiaotao.saltedfishcloud.model.po.CollectionInfo;
import com.xiaotao.saltedfishcloud.model.po.CollectionInfoId;
import com.xiaotao.saltedfishcloud.model.po.CollectionRecord;
import com.xiaotao.saltedfishcloud.model.po.NodeInfo;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystem;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.service.node.NodeService;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Optional;


@Service
@RequiredArgsConstructor
public class CollectionServiceImpl implements CollectionService {
    private final CollectionInfoRepo collectionDao;
    private final CollectionRecordRepo recordDao;
    private final NodeService nodeService;
    private final DiskFileSystemManager fileSystem;

    @Override
    public Page<CollectionRecord> getSubmits(Long cid, int page, int size) {
        CollectionRecord record = new CollectionRecord();
        record.setCid(cid);
        return recordDao.findByCid(cid, PageRequest.of(page, size));
    }

    @Override
    public void deleteCollection(int uid, long cid) {
        CollectionInfo collection = collectionDao.findById(cid).orElse(null);
        if (collection == null) throw new JsonException(CollectionError.COLLECTION_NOT_FOUND);
        if (!collection.getUid().equals(uid)) throw new JsonException(CommonError.FORMAT_ERROR);
        collectionDao.deleteById(cid);
    }

    @Override
    public CollectionInfo setState(int uid, Long cid, CollectionInfo.State state) {
        CollectionInfo info = collectionDao.findById(cid).orElse(null);
        if (info == null) throw new JsonException(CollectionError.COLLECTION_NOT_FOUND);
        if (!info.getUid().equals(uid)) throw new JsonException(CommonError.SYSTEM_FORBIDDEN);
        info.setState(state);
        collectionDao.save(info);
        return info;
    }
    @Override
    public CollectionInfoId createCollection(int uid, CollectionDTO info) {
        if(!CollectionValidator.validateCreate(info)) {
            throw new JsonException(CollectionError.COLLECTION_CHECK_FAILED);
        }
        NodeInfo node = nodeService.getNodeById(uid, info.getSaveNode());
        if (node == null) {
            throw new JsonException(FileSystemError.NODE_NOT_FOUND);
        }
        String savePath = nodeService.getPathByNode(uid, node.getId());
        CollectionInfo ci = new CollectionInfo(uid, info);
        ci.setVerification(SecureUtils.getUUID());

        ci.setSavePathSnapshot(savePath);
        collectionDao.save(ci);
        return new CollectionInfoId(ci.getId(), ci.getVerification());
    }

    @Override
    public CollectionInfo getCollection(Long cid) {
        return collectionDao.findById(cid).orElse(null);
    }

    @Override
    public CollectionInfo getCollectionWitchVerification(CollectionInfoId cid) {
        Optional<CollectionInfo> r = collectionDao.findById(cid.getId());

        CollectionInfo info = r.orElse(null);
        if (info == null) return null;
        if (info.getVerification().equals(cid.getVerification())) {
            return info;
        } else {
            return null;
        }
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public void collectFile(CollectionInfoId cid, int uid, InputStream is, FileInfo fileInfo, SubmitFile submitFile, String ip) throws IOException {
        CollectionInfo ci = collectionDao.findById(cid.getId()).orElse(null);

        // 校验收集存在
        if (ci == null) { throw new JsonException(CollectionError.COLLECTION_NOT_FOUND); }

        // 校验开关状态
        if (ci.getState() == CollectionInfo.State.CLOSED) {
            throw new JsonException(CollectionError.COLLECTION_CLOSED);
        }

        // 校验过期
        if (ci.getExpiredAt().compareTo(new Date()) < 0) {
            throw new CollectionCheckedException("收集已于" + ci.getExpiredAt() + "过期");
        }

        // 校验匿名状态
        if (!ci.getAllowAnonymous() && uid == 0) { throw new JsonException(CollectionError.COLLECTION_REQUIRE_LOGIN); }

        // 校验约束
        if (!CollectionValidator.validateSubmit(ci, submitFile)) { throw new JsonException(CollectionError.COLLECTION_CHECK_FAILED); }

        // 校验收集数
        Integer allowMax = ci.getAllowMax();
        if (allowMax != null && allowMax > -1) {
            // 收集满
            if (ci.getAvailable() == 0) {
                throw new JsonException(CollectionError.COLLECTION_FULL);
            }

            int res = collectionDao.consumeCount(cid.getId(), ci.getAvailable());

            // 乐观锁操作失败
            if (res == 0) {
                throw new JsonException(CommonError.SYSTEM_BUSY);
            }

            // 收集完最后一个，状态设为已关闭
            if (ci.getAvailable() == 1) {
                ci.setAvailable(0);
                ci.setState(CollectionInfo.State.CLOSED);
                collectionDao.save(ci);
            }
        }

        if (fileInfo.getMd5() == null) fileInfo.updateMd5();


        String filename = CollectionParser.parseFilename(ci, submitFile);
        CollectionRecord record = new CollectionRecord(cid.getId(), uid, filename, submitFile.getSize(), fileInfo.getMd5(), ip);

        DiskFileSystem fileSystem = this.fileSystem.getMainFileSystem();
        String path = nodeService.getPathByNode(ci.getUid(), ci.getSaveNode());
        String[] pair = FileUtils.parseName(filename);

        int cnt = 1;
        while (fileSystem.exist(ci.getUid(), path + "/" + filename)) {
            filename = pair[0] + "_" + cnt + (pair[1] == null ? "" : ("." + pair[1]));
            cnt++;
        }
        record.setFilename(filename);
        recordDao.save(record);
        fileInfo.setName(filename);
        // 存入文件
        fileSystem.saveFile(ci.getUid(), is, path, fileInfo);
    }
}
