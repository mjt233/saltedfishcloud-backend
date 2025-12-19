package com.xiaotao.saltedfishcloud.service.collection;

import com.xiaotao.saltedfishcloud.constant.ResourceProtocol;
import com.xiaotao.saltedfishcloud.constant.error.CollectionError;
import com.xiaotao.saltedfishcloud.dao.jpa.CollectionInfoRepo;
import com.xiaotao.saltedfishcloud.dao.jpa.CollectionRecordRepo;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.helper.OutputStreamConsumer;
import com.xiaotao.saltedfishcloud.model.PermissionInfo;
import com.xiaotao.saltedfishcloud.model.dto.ResourceRequest;
import com.xiaotao.saltedfishcloud.model.param.FileCollectionResourceParam;
import com.xiaotao.saltedfishcloud.model.po.CollectionInfo;
import com.xiaotao.saltedfishcloud.model.po.CollectionInfoId;
import com.xiaotao.saltedfishcloud.model.po.CollectionRecord;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.service.node.NodeService;
import com.xiaotao.saltedfishcloud.service.resource.AbstractWritableResourceProtocolHandler;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Component
public class CollectionProtocolHandler extends AbstractWritableResourceProtocolHandler<FileCollectionResourceParam> {
    @Autowired
    private CollectionService collectionService;

    @Autowired
    private CollectionRecordRepo collectionRecordRepo;

    @Autowired
    private DiskFileSystemManager diskFileSystemManager;

    @Autowired
    private NodeService nodeService;

    @Autowired
    private CollectionInfoRepo collectionDao;

    private CollectionRecordRepo recordDao;

    @Override
    public boolean isWriteable() {
        return true;
    }

    @Override
    public PermissionInfo getPermissionInfo(ResourceRequest resourceRequest, FileCollectionResourceParam param) {
        return PermissionInfo.builder()
                .ownerUid(param.getCollectionInfo().getUid())
                .isWritable(true)
                .isReadable(true)
                .build();
    }

    @Override
    public Resource getFileResource(ResourceRequest resourceRequest, FileCollectionResourceParam param) throws IOException {
        CollectionInfo collectionInfo = param.getCollectionInfo();
        CollectionRecord record = Objects.requireNonNull(param.getCollectionRecord(), "找不到文件提交记录");
        // 获取收集人用户id
        Long uid = collectionInfo.getUid();

        return Optional
                // 1. 优先按提交的文件md5查找文件
                .ofNullable(diskFileSystemManager.getMainFileSystem().getResourceByMd5(record.getMd5()))

                // 2. 按md5找不到文件，说明文件可能提交到了挂载目录下。按采集目录的路径 + 提交文件查找文件
                .or(() -> {

                    log.warn("文件采集无法按提交的文件md5查询到文件 cid - {} recordId - {}", collectionInfo.getId(), record.getId());
                    return Optional.ofNullable(collectionInfo.getSaveNode())
                            .map(nodeId -> nodeService.getPathByNode(uid, nodeId))
                            .map(path -> {
                                try {
                                    return diskFileSystemManager.getMainFileSystem().getResource(uid, path, record.getFilename());
                                } catch (IOException e) {
                                    log.warn("获取提交的文件采集资源失败 cid - {} recordId - {}", collectionInfo.getId(), record.getId(), e);
                                    return null;
                                }
                            });
                })

                // 3. 还是找不到，文件或目录可能被移动过 或 系统未使用文件记录服务，使用创建采集时的路径快照再试一次
                .or(() -> {
                    log.warn("使用 savePathSnapshot 查找提交的文件采集 cid - {} recordId - {}", collectionInfo.getId(), record.getId());
                    try {
                        return Optional.ofNullable(diskFileSystemManager.getMainFileSystem().getResource(uid, collectionInfo.getSavePathSnapshot(), record.getFilename()));
                    } catch (IOException e) {
                        log.warn("按文件采集提交快照路径 savePathSnapshot 获取提交的文件采集资源失败 cid - {} recordId - {}", collectionInfo.getId(), record.getId());
                        return Optional.empty();
                    }
                })
                .orElse(null);
    }

    @Override
    public String getPathMappingIdentity(ResourceRequest resourceRequest, FileCollectionResourceParam param) {
        CollectionInfo collectionInfo = param.getCollectionInfo();
        CollectionRecord record = Objects.requireNonNull(param.getCollectionRecord(), "找不到文件提交记录");
        // 获取收集人用户id
        Long uid = collectionInfo.getUid();

        String path = Optional.ofNullable(collectionInfo.getSaveNode())
                .map(nodeId -> {
                    try {
                        return nodeService.getPathByNode(uid, nodeId);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .orElse(collectionInfo.getSavePathSnapshot());

        return SecureUtils.getMd5(uid + ":" + StringUtils.appendPath(path, record.getFilename()));
    }

    @Override
    public FileCollectionResourceParam validAndParseParam(ResourceRequest resourceRequest, boolean isWrite) {
        try {
            FileCollectionResourceParam parsedParam = MapperHolder.parseJson(MapperHolder.toJson(resourceRequest.getParams()), FileCollectionResourceParam.class);
            parsedParam.setCid(Long.valueOf(resourceRequest.getTargetId()));
            CollectionInfo info = collectionService.getCollectionWitchVerification(new CollectionInfoId(parsedParam.getCid(), parsedParam.getVerification()));
            if (info == null) {
                throw new JsonException(CollectionError.COLLECTION_NOT_FOUND);
            }
            if (parsedParam.getType() == FileCollectionResourceParam.Type.SUBMIT || isWrite) {
                // 校验开关状态
                if (info.getState() == CollectionInfo.State.CLOSED) {
                    throw new JsonException(CollectionError.COLLECTION_CLOSED);
                }

                // 校验过期
                if (info.getExpiredAt().compareTo(new Date()) < 0) {
                    throw new CollectionCheckedException("收集已于" + info.getExpiredAt() + "过期");
                }


                // 校验匿名状态
                if (!info.getAllowAnonymous() && SecureUtils.getCurrentUid() == null) { throw new JsonException(CollectionError.COLLECTION_REQUIRE_LOGIN); }

                // 校验约束
                if (!CollectionValidator.validateSubmit(info, parsedParam.getSubmitFile())) { throw new JsonException(CollectionError.COLLECTION_CHECK_FAILED); }
            }

            parsedParam.setCollectionInfo(info);
            if (parsedParam.getType() == FileCollectionResourceParam.Type.GET) {
                parsedParam.setCollectionRecord(this.getRecordById(resourceRequest.getTargetId()));
            }
            return parsedParam;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private CollectionRecord getRecordById(String id) {
        return collectionRecordRepo.findById(Long.valueOf(id)).orElseThrow(() -> new JsonException("不存在的文件提交记录"));
    }

    @Override
    public String getProtocolName() {
        return ResourceProtocol.COLLECTION;
    }

    @Override
    public void handleWriteResource(ResourceRequest resourceRequest, FileCollectionResourceParam parsedParam, OutputStreamConsumer<OutputStream> streamConsumer) throws IOException {
        HttpServletRequest request = ((ServletRequestAttributes) Objects.requireNonNull(RequestContextHolder.getRequestAttributes())).getRequest();
        String ip = request.getRemoteAddr();
        FileInfo fileInfo = createFileInfoFromRequest(resourceRequest, null);
        collectionService.collectFile(
                new CollectionInfoId(parsedParam.getCid(), parsedParam.getVerification()),
                SecureUtils.getCurrentUid(),
                fileInfo,
                parsedParam.getSubmitFile(),
                ip,
                streamConsumer
        );
    }
}
