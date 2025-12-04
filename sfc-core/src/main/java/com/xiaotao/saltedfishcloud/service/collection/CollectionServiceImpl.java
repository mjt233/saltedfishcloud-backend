package com.xiaotao.saltedfishcloud.service.collection;

import com.xiaotao.saltedfishcloud.constant.error.CollectionError;
import com.xiaotao.saltedfishcloud.constant.error.CommonError;
import com.xiaotao.saltedfishcloud.constant.error.FileSystemError;
import com.xiaotao.saltedfishcloud.dao.jpa.CollectionInfoRepo;
import com.xiaotao.saltedfishcloud.dao.jpa.CollectionRecordRepo;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.helper.OutputStreamConsumer;
import com.xiaotao.saltedfishcloud.model.dto.CollectionDTO;
import com.xiaotao.saltedfishcloud.model.dto.SubmitFile;
import com.xiaotao.saltedfishcloud.model.po.CollectionInfo;
import com.xiaotao.saltedfishcloud.model.po.CollectionInfoId;
import com.xiaotao.saltedfishcloud.model.po.CollectionRecord;
import com.xiaotao.saltedfishcloud.model.po.NodeInfo;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystem;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.service.node.NodeService;
import com.xiaotao.saltedfishcloud.utils.DiskFileSystemUtils;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
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
    public void deleteCollection(long uid, long cid) {
        CollectionInfo collection = collectionDao.findById(cid).orElse(null);
        if (collection == null) throw new JsonException(CollectionError.COLLECTION_NOT_FOUND);
        if (!collection.getUid().equals(uid)) throw new JsonException(CommonError.FORMAT_ERROR);
        collectionDao.deleteById(cid);
    }

    @Override
    public CollectionInfo setState(long uid, Long cid, CollectionInfo.State state) {
        CollectionInfo info = collectionDao.findById(cid).orElse(null);
        if (info == null) throw new JsonException(CollectionError.COLLECTION_NOT_FOUND);
        if (!info.getUid().equals(uid)) throw new JsonException(CommonError.SYSTEM_FORBIDDEN);
        info.setState(state);
        collectionDao.save(info);
        return info;
    }
    @Override
    public CollectionInfoId createCollection(long uid, CollectionDTO info) {
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
    public void collectFile(CollectionInfoId cid, long providerUid, FileInfo fileInfo, SubmitFile submitFile, String ip, OutputStreamConsumer<OutputStream> streamConsumer) throws IOException {
        CollectionInfo collectionInfo = collectionDao.findById(cid.getId()).orElse(null);
        fileInfo.setUid(providerUid);

        // 检查能否提交
        this.checkIsCanSubmit(collectionInfo, submitFile, providerUid);

        // 保存提交的文件
        this.saveFile(collectionInfo, submitFile, fileInfo, streamConsumer);

        // 保存提交记录
        this.addCollectionRecord(cid, providerUid, fileInfo, submitFile, ip);
    }

    @Override
    public void collectFile(CollectionInfoId cid, long providerUid, FileInfo fileInfo, SubmitFile submitFile, String ip) throws IOException {
        // 保存提交的文件
        this.collectFile(cid, providerUid, fileInfo, submitFile, ip, os -> DiskFileSystemUtils.saveFile(fileInfo, os));
    }

    /**
     * 收到采集的文件后，添加一条采集记录
     * @param cid   文件采集任务信息
     * @param providerUid   提供人
     * @param fileInfo  提供的文件
     * @param submitFile    表单提交信息
     * @param ip    提交人ip地址
     */
    private void addCollectionRecord(CollectionInfoId cid, long providerUid, FileInfo fileInfo, SubmitFile submitFile, String ip) {
        CollectionRecord record = new CollectionRecord(cid.getId(), providerUid, fileInfo.getName(), submitFile.getFileParam().getSize(), fileInfo.getMd5(), ip);
        record.setFilename(fileInfo.getName());
        recordDao.save(record);
    }

    /**
     * 将提交的采集文件保存到网盘系统。如果提交的文件在网盘中出现重名的，会自动对文件进行重命名，并修改fileInfo参数的name
     * @param collectionInfo    采集信息
     * @param submitFile    文件提交表单信息
     * @param fileInfo  提交的文件
     * @param streamConsumer 网盘的文件输出流处理函数
     */
    private void saveFile(CollectionInfo collectionInfo, SubmitFile submitFile, FileInfo fileInfo, OutputStreamConsumer<OutputStream> streamConsumer) throws IOException {
        // 解析文件名变量并重命名
        String filename = CollectionParser.parseFilename(collectionInfo, submitFile);
        DiskFileSystem fileSystem = this.fileSystem.getMainFileSystem();
        String path = nodeService.getPathByNode(collectionInfo.getUid(), collectionInfo.getSaveNode());
        String[] pair = FileUtils.parseName(filename);

        int cnt = 1;
        while (fileSystem.exist(collectionInfo.getUid(), path + "/" + filename)) {
            filename = pair[0] + "_" + cnt + (pair[1] == null ? "" : ("." + pair[1]));
            cnt++;
        }
        fileInfo.setName(filename);
        fileInfo.setUid(collectionInfo.getUid());
        fileSystem.saveFileByStream(fileInfo, path, streamConsumer);

        // 保存成功后，消费可收集数。如果消费失败则把文件删掉。
        if(!this.consumeCount(collectionInfo)) {
            fileSystem.deleteFile(fileInfo.getUid(), path, Collections.singletonList(fileInfo.getName()));
            throw new JsonException(CollectionError.COLLECTION_FULL);
        }
    }

    /**
     * 检查是否满足文件提交条件
     * @param collectionInfo    文件收集任务信息
     * @param submitFile    提交的文件表单
     * @param providerUid   提交人
     */
    private void checkIsCanSubmit(CollectionInfo collectionInfo, SubmitFile submitFile, Long providerUid) {

        // 校验收集存在
        if (collectionInfo == null) { throw new JsonException(CollectionError.COLLECTION_NOT_FOUND); }

        // 校验开关状态
        if (collectionInfo.getState() == CollectionInfo.State.CLOSED) {
            throw new JsonException(CollectionError.COLLECTION_CLOSED);
        }

        // 校验过期
        if (collectionInfo.getExpiredAt().compareTo(new Date()) < 0) {
            throw new CollectionCheckedException("收集已于" + collectionInfo.getExpiredAt() + "过期");
        }

        // 校验匿名状态
        if (!collectionInfo.getAllowAnonymous() && providerUid == 0) { throw new JsonException(CollectionError.COLLECTION_REQUIRE_LOGIN); }

        // 校验约束
        if (!CollectionValidator.validateSubmit(collectionInfo, submitFile)) { throw new JsonException(CollectionError.COLLECTION_CHECK_FAILED); }

        // 校验收集数是否够了
        this.checkCollectCountIsFull(collectionInfo);
    }

    /**
     * 检查文件收集任务是否已满
     */
    private void checkCollectCountIsFull(CollectionInfo collectionInfo) {
        Integer allowMax = collectionInfo.getAllowMax();
        if (allowMax != null && allowMax > -1) {
            // 收集满
            if (collectionInfo.getAvailable() == 0) {
                throw new JsonException(CollectionError.COLLECTION_FULL);
            }
        }
    }

    /**
     * 消费一个采集任务的可采集次数
     * @return 是否消费成功
     */
    private boolean consumeCount(CollectionInfo collectionInfo) {

        // 无限制采集次数
        Integer allowMax = collectionInfo.getAllowMax();
        if (allowMax == null || allowMax <= -1) {
            return true;
        }

        while (collectionInfo.getAvailable() > 0) {
            int res = collectionDao.consumeCount(collectionInfo.getId(), collectionInfo.getAvailable());
            // 乐观锁操作失败
            if (res == 0) {
                BeanUtils.copyProperties(collectionDao.getReferenceById(collectionInfo.getId()), collectionInfo);
                collectionInfo.setAvailable(collectionInfo.getAvailable() - 1);
                continue;
            }

            // 乐观锁操作成功
            // 收集完最后一个，状态设为已关闭
            if (collectionInfo.getAvailable() == 1) {
                collectionInfo.setAvailable(0);
                collectionInfo.setState(CollectionInfo.State.CLOSED);
                collectionDao.save(collectionInfo);
            }
            return true;
        }
        return false;
    }
}
