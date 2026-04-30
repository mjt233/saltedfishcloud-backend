package com.sfc.archive.service;

import com.sfc.archive.ArchiveEngineCompressor;
import com.sfc.archive.ArchiveEngineManager;
import com.sfc.archive.ArchiveEngineProvider;
import com.sfc.archive.model.ArchiveEngineProperty;
import com.sfc.archive.model.ArchiveResource;
import com.sfc.archive.model.CompressionLevel;
import com.sfc.archive.model.DiskFileSystemCompressParam;
import com.sfc.archive.utils.EngineResourceUtils;
import com.sfc.task.AsyncTaskManager;
import com.sfc.task.model.AsyncTaskRecord;
import com.xiaotao.saltedfishcloud.config.SysProperties;
import com.xiaotao.saltedfishcloud.constant.AsyncTaskType;
import com.xiaotao.saltedfishcloud.model.po.User;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.model.template.BaseModel;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystem;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.utils.DiskFileSystemUtils;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
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
                    addDirectoryResources(fileSystem, compressor, uid, path, fileInfo);
                }
            }
        }
    }

    /**
     * 遍历目录并在遍历完成后仅写入空目录项。
     * <p>
     * 目录在遍历过程中先全部记录到候选集合中；若某目录在遍历时发现存在直接子项，
     * 则加入非空目录集合。遍历结束后，仅将不在非空目录集合中的目录写入压缩包，
     * 从而保留空目录，同时避免为非空目录重复创建显式目录项。
     * </p>
     *
     * @param fileSystem     文件系统
     * @param compressor     压缩器
     * @param uid            用户 ID
     * @param sourceBasePath 源目录路径
     * @param rootDirectory  起始目录
     * @throws IOException 遍历或写入失败
     */
    private void addDirectoryResources(DiskFileSystem fileSystem, ArchiveEngineCompressor compressor, long uid, String sourceBasePath, FileInfo rootDirectory) throws IOException {
        String rootDirectoryPath = StringUtils.appendPath(sourceBasePath, rootDirectory.getName());
        Map<String, ArchiveResource> candidateDirectoryResources = new LinkedHashMap<>();
        candidateDirectoryResources.put(rootDirectoryPath, EngineResourceUtils.toArchiveResource(rootDirectory, "/", null));

        DiskFileSystemUtils.walk(fileSystem, uid, rootDirectoryPath, (curPath, subFiles) -> {
            if (!subFiles.isEmpty()) {
                candidateDirectoryResources.remove(curPath);
            }

            String basePath = StringUtils.removePrefix(sourceBasePath, curPath);
            if (basePath.isEmpty()) {
                basePath = "/";
            }

            for (FileInfo subFile : subFiles) {
                if (subFile.isFile()) {
                    compressor.addArchiveResource(EngineResourceUtils.toArchiveResource(subFile, basePath, fileSystem.getResource(uid, curPath, subFile.getName())));
                } else {
                    String subDirectoryPath = StringUtils.appendPath(curPath, subFile.getName());
                    candidateDirectoryResources.put(subDirectoryPath, EngineResourceUtils.toArchiveResource(subFile, basePath, null));
                }
            }
        });

        for (Map.Entry<String, ArchiveResource> entry : candidateDirectoryResources.entrySet()) {
            compressor.addArchiveResource(entry.getValue());
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
