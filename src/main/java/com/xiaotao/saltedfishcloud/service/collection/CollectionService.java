package com.xiaotao.saltedfishcloud.service.collection;

import com.xiaotao.saltedfishcloud.dao.jpa.CollectionInfoRepository;
import com.xiaotao.saltedfishcloud.dao.jpa.CollectionRecordRepo;
import com.xiaotao.saltedfishcloud.entity.ErrorInfo;
import com.xiaotao.saltedfishcloud.entity.dto.CollectionDTO;
import com.xiaotao.saltedfishcloud.entity.dto.SubmitFile;
import com.xiaotao.saltedfishcloud.entity.po.CollectionInfo;
import com.xiaotao.saltedfishcloud.entity.po.CollectionInfoId;
import com.xiaotao.saltedfishcloud.entity.po.CollectionRecord;
import com.xiaotao.saltedfishcloud.entity.po.NodeInfo;
import com.xiaotao.saltedfishcloud.entity.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.service.file.filesystem.DiskFileSystem;
import com.xiaotao.saltedfishcloud.service.file.filesystem.DiskFileSystemFactory;
import com.xiaotao.saltedfishcloud.service.node.NodeService;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.RequiredArgsConstructor;
import org.aspectj.util.FileUtil;
import org.springframework.data.domain.Example;
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
public class CollectionService {
    private final CollectionInfoRepository collectionDao;
    private final CollectionRecordRepo recordDao;
    private final NodeService nodeService;
    private final DiskFileSystemFactory fileSystem;

    /**
     * 获取某个文件收集接收到的文件记录
     * @param cid   收集ID
     * @param page  页码，从0开始
     * @param size  每页的大小
     * @return      分页信息
     */
    public Page<CollectionRecord> getSubmits(Long cid, int page, int size) {
        CollectionRecord record = new CollectionRecord();
        record.setCid(cid);
        return recordDao.findAll(Example.of(record), PageRequest.of(page, size));
    }

    /**
     * 关闭一个收集任务，停止收集
     * @param uid   调用者用户ID
     * @param cid   收集ID
     * @return  关闭后的收集任务信息
     */
    public CollectionInfo closeCollection(int uid, Long cid) {
        CollectionInfo info = collectionDao.findById(cid).orElse(null);
        if (info == null) throw new JsonException(ErrorInfo.COLLECTION_NOT_FOUND);
        if (!info.getUid().equals(uid)) {
            throw new JsonException(ErrorInfo.SYSTEM_FORBIDDEN);
        }
        info.setState(CollectionInfo.State.CLOSED);
        collectionDao.save(info);
        return info;
    }
    /**
     * 创建收集任务
     * @param uid   创建者ID
     * @param info  收集任务信息
     * @return      收集任务ID
     */
    public CollectionInfoId createCollection(int uid, CollectionDTO info) {
        if(!CollectionValidator.validateCreate(info)) {
            throw new JsonException(ErrorInfo.COLLECTION_CHECK_FAILED);
        }
        NodeInfo node = nodeService.getNodeById(uid, info.getSaveNode());
        if (node == null) {
            throw new JsonException(ErrorInfo.NODE_NOT_FOUND);
        }
        String savePath = nodeService.getPathByNode(uid, node.getId());
        CollectionInfo ci = new CollectionInfo(uid, info);
        ci.setVerification(SecureUtils.getUUID());

        ci.setSavePathSnapshot(savePath);
        collectionDao.save(ci);
        return new CollectionInfoId(ci.getId(), ci.getVerification());
    }

    /**
     * 获取一个收集信息
     * @param cid   收集ID
     * @return      收集信息，若收集id不存在，则返回null
     */
    public CollectionInfo getCollection(Long cid) {
        return collectionDao.findById(cid).orElse(null);
    }

    /**
     * 获取一个收集信息，此方法要求同时使用ID和验证码
     * @param cid   收集ID
     * @return      收集信息，若收集id不存在，则返回null
     */
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

    /**
     * 接受一个文件存到集合中
     * @param cid   收集ID
     * @param uid   提供者ID，游客使用0
     * @param is    文件输入流
     * @param fileInfo      基础文件信息（至少应包含文件名，大小，md5）
     * @param ip            提交者的IP地址
     * @param submitFile    提交的文件信息
     */
    @Transactional(rollbackFor = Throwable.class)
    public void collectFile(CollectionInfoId cid, int uid, InputStream is, FileInfo fileInfo, SubmitFile submitFile, String ip) throws IOException {
        CollectionInfo ci = collectionDao.findById(cid.getId()).orElse(null);

        // 校验收集存在
        if (ci == null) { throw new JsonException(ErrorInfo.COLLECTION_NOT_FOUND); }

        // 校验开关状态
        if (ci.getState() == CollectionInfo.State.CLOSED) {
            throw new JsonException(ErrorInfo.COLLECTION_CLOSED);
        }

        // 校验过期
        if (ci.getExpiredAt().compareTo(new Date()) < 0) {
            throw new CollectionCheckedException("收集已于" + ci.getExpiredAt() + "过期");
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

            int res = collectionDao.consumeCount(cid.getId(), ci.getAvailable());

            // 乐观锁操作失败
            if (res == 0) {
                throw new JsonException(ErrorInfo.SYSTEM_BUSY);
            }

            // 收集完最后一个，状态设为已关闭
            if (ci.getAvailable() == 1) {
                ci.setState(CollectionInfo.State.CLOSED);
                collectionDao.save(ci);
            }
        }

        if (fileInfo.getMd5() == null) fileInfo.updateMd5();


        String filename = CollectionParser.parseFilename(ci, submitFile);
        CollectionRecord record = new CollectionRecord(cid.getId(), uid, filename, submitFile.getSize(), fileInfo.getMd5(), ip);

        DiskFileSystem fileSystem = this.fileSystem.getFileSystem();
        String path = nodeService.getPathByNode(ci.getUid(), ci.getSaveNode());
        String[] pair = FileUtils.parseName(filename);

        int cnt = 1;
        while (fileSystem.exist(ci.getUid(), path + "/" + filename)) {
            filename = pair[0] + "_" + cnt + (pair[1] == null ? "" : ("." + pair[1]));
            cnt++;
        }
        recordDao.save(record);
        fileInfo.setName(filename);
        // 存入文件
        fileSystem.saveFile(ci.getUid(), is, path, fileInfo);
    }
}
