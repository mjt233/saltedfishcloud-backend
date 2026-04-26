package com.sfc.archive.service;

import com.sfc.archive.*;
import com.sfc.archive.model.*;
import com.sfc.archive.utils.EngineResourceUtils;
import com.xiaotao.saltedfishcloud.constant.AsyncTaskType;
import com.sfc.task.AsyncTaskManager;
import com.sfc.task.model.AsyncTaskRecord;
import com.xiaotao.saltedfishcloud.config.SysProperties;
import com.xiaotao.saltedfishcloud.model.po.User;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.model.template.BaseModel;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystem;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.utils.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 默认的网盘文件系统压缩、解压缩服务实现类
 */
@Slf4j
@Service
public class DiskFileSystemArchiveServiceImpl implements DiskFileSystemArchiveService {
    @Autowired
    private DiskFileSystemManager diskFileSystemManager;

    @Autowired
    private SysProperties sysProperties;

    @Autowired
    private ArchiveEngineManager archiveEngineManager;

    @Autowired
    private AsyncTaskManager asyncTaskManager;


    /**
     * 将多个文件通过zip压缩，并将压缩结果输出到outputStream
     * @param uid   待压缩的文件的用户id
     * @param path  待压缩的文件的所在网盘目录路径
     * @param names path下待压缩的文件名列表
     * @param compressionLevel  压缩等级
     * @param outputStream  压缩结果输出流
     */
    public void doCompressZipAndWriteOut(long uid, String path, Collection<String> names, CompressionLevel compressionLevel, OutputStream outputStream) throws IOException {
        ArchiveEngineProvider engine = archiveEngineManager.getDecompressProviderByFilename("tmp.zip")
                .stream()
                .findAny()
                .orElseThrow(() -> new UnsupportedOperationException("找不到支持zip的ArchiveEngine"));

        DiskFileSystem fileSystem = diskFileSystemManager.getMainFileSystem();
        try(ArchiveEngineCompressor compressor = engine.createCompressor(outputStream, ArchiveEngineProperty.builder()
                .compressionLevel(compressionLevel)
                .encoding(sysProperties.getStore().getArchiveEncoding())
                .build())) {
            for (FileInfo fileInfo : fileSystem.getUserFileList(uid, path, names)) {
                if (fileInfo.isFile()) {
                    compressor.addArchiveResource(EngineResourceUtils.toArchiveResource(fileInfo, "/", fileSystem.getResource(uid, path, fileInfo.getName())));
                } else {
                    // 创建文件夹本身
                    compressor.addArchiveResource(EngineResourceUtils.toArchiveResource(fileInfo, "/", null));
                    DiskFileSystemUtils.walk(fileSystem, uid, StringUtils.appendPath(path, fileInfo.getName()), (curPath, subFiles) -> {
                        String basePath = StringUtils.removePrefix(path, curPath);
                        if (basePath.isEmpty()) {
                            basePath = "/";
                        }

                        // 向文件夹内压缩文件
                        for (FileInfo subFile : subFiles) {
                            if (subFile.isFile()) {
                                compressor.addArchiveResource(EngineResourceUtils.toArchiveResource(subFile, basePath, fileSystem.getResource(uid, curPath, subFile.getName())));
                            } else {
                                compressor.addArchiveResource(EngineResourceUtils.toArchiveResource(subFile, basePath, null));
                            }
                        }
                    });
                }
            }
        }
    }


    @Override
    public void compressAndWriteOut(long uid, String path, Collection<String> names, OutputStream outputStream) throws IOException {
        doCompressZipAndWriteOut(uid, path, names, CompressionLevel.STORE, outputStream);
    }

    @Override
    public long asyncCompress(DiskFileSystemCompressParam param) throws IOException {
        AsyncTaskRecord record = new AsyncTaskRecord();
        record.setName(AsyncTaskType.ARCHIVE_COMPRESS + "-" + param.getEngineProviderId());
        record.setTaskType(AsyncTaskType.ARCHIVE_COMPRESS);
        record.setCpuOverhead(10);
        record.setParams(MapperHolder.toJson(param));
        User curUser = SecureUtils.getSpringSecurityUser();
        record.setUid(Optional.ofNullable(curUser).map(BaseModel::getId).orElse(param.getSourceUid()));

        asyncTaskManager.submitAsyncTask(record);

        if (Boolean.TRUE.equals(param.getWaitExit())) {
            try {
                return Optional.ofNullable(asyncTaskManager.waitTaskExit(record.getId(), 1, TimeUnit.HOURS))
                        .orElse(record)
                        .getId();
            } catch (InterruptedException e) {
                throw new IOException(e);
            }
        }
        return record.getId();
    }
}
