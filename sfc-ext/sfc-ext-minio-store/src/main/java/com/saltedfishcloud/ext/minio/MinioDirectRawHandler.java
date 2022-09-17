package com.saltedfishcloud.ext.minio;

import com.saltedfishcloud.ext.minio.utils.MinioUtils;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.store.DirectRawStoreHandler;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import io.minio.*;
import io.minio.messages.Item;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 基于Minio的存储直接操作器
 */
@Slf4j
public class MinioDirectRawHandler implements DirectRawStoreHandler {
    private final MinioClient client;
    private final MinioProperties properties;
    private final static String LOG_PREFIX = "[Minio]";

    /**
     * 构造Minio存储操作器
     * @param client        minio客户端
     * @param properties    minio配置参数
     */
    public MinioDirectRawHandler(MinioClient client, MinioProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public boolean isEmptyDirectory(String path) throws IOException {
        try {
            Iterable<Result<Item>> results = client.listObjects(ListObjectsArgs.builder()
                    .bucket(properties.getBucket())
                    .prefix(MinioUtils.toDirectoryName(path))
                    .maxKeys(2)
                    .build());
            int cnt=0;
            for (Result<Item> ignored : results) {
                cnt++;
            }
            return cnt == 1;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    @Override
    public Resource getResource(String path) throws IOException {
        try {
            MinioResource resource = MinioUtils.getObjectResource(client, properties.getBucket(), path);
            if (resource != null && resource.isDir()) {
                return null;
            }
            return resource;
        } catch (Exception e) {
            if (e instanceof IOException) {
                throw (IOException) e;
            } else {
                throw new IOException(e);
            }
        }
    }

    @Override
    public List<FileInfo> listFiles(String path) throws IOException {
        String objectName = MinioUtils.toMinioObjectName(path);
        Iterable<Result<Item>> results = client.listObjects(ListObjectsArgs.builder()
                .bucket(objectName)
                .prefix(MinioUtils.toDirectoryName(path))
                .build());

        List<FileInfo> fileInfos = new ArrayList<>();
        for (Result<Item> result : results) {
            try {
                Item item = result.get();
                fileInfos.add(MinioUtils.itemToFileInfo(client, properties.getBucket(), item));
            } catch (Exception e) {
                if (MinioUtils.isNotFound(e)) {
                    return Collections.emptyList();
                } else {
                    throw MinioUtils.toIOException(e);
                }

            }
        }
        return fileInfos;
    }

    @Override
    public boolean exist(String path) {
        try {
            log.debug("{}<-检测是否存在：{}", LOG_PREFIX, path);
            StatObjectResponse stat = MinioUtils.getStat(client, properties.getBucket(), path);
            if(stat != null) {
                log.debug("{}->存在对象：{}", LOG_PREFIX, stat.object());
                return true;
            }
            Iterable<Result<Item>> results = client.listObjects(ListObjectsArgs.builder()
                    .bucket(properties.getBucket())
                    .prefix(MinioUtils.toDirectoryName(path))
                    .maxKeys(1)
                    .build());
            for (Result<Item> item : results) {
                log.debug("{}->存在子对象：{}",LOG_PREFIX, item.get().objectName());
                return true;
            }
            log.debug("{}->对象不存在：{}", LOG_PREFIX, path);
            return false;
        } catch (Exception e) {
            if (MinioUtils.isNotFound(e)) {
                return false;
            } else {
                throw new RuntimeException(e);
            }
        }

    }

    @Override
    public FileInfo getFileInfo(String path) throws IOException {
        try {
            MinioResource resource = MinioUtils.getObjectResource(client, properties.getBucket(), path);
            if (resource == null) {
                return null;
            }

            String name = StringUtils.getURLLastName(resource.getFilename());
            return new FileInfo(
                    name,
                    resource.contentLength(),
                    resource.isDir() ? FileInfo.TYPE_DIR : FileInfo.TYPE_FILE,
                    path,
                    resource.lastModified(),
                    resource
            );
        } catch (Exception e) {
            if (MinioUtils.isNotFound(e)) {
                return null;
            } else {
                throw MinioUtils.toIOException(e);
            }
        }
    }


    @Override
    public boolean delete(String path) throws IOException {
        try {
            log.debug("{}<-删除操作：{}", LOG_PREFIX, path);
            Iterable<Result<Item>> items = client.listObjects(ListObjectsArgs.builder()
                    .bucket(properties.getBucket())
                    .prefix(MinioUtils.toDirectoryName(path))
                    .recursive(true)
                    .build());
            for (Result<Item> item : items) {
                String objectName = item.get().objectName();
                log.debug("{}->删除子项：{}", LOG_PREFIX, objectName);
                client.removeObject(RemoveObjectArgs.builder().bucket(properties.getBucket())
                        .object(objectName)
                        .build());
            }
            client.removeObject(RemoveObjectArgs.builder().bucket(properties.getBucket())
                    .object(MinioUtils.toMinioObjectName(path))
                    .build());
            log.debug("{}->删除对象：{}", LOG_PREFIX, path);
            return true;
        } catch (Exception e) {
            if (MinioUtils.isNotFound(e)) {
                return false;
            } else {
                throw MinioUtils.toIOException(e);
            }
        }
    }

    @Override
    public boolean mkdir(String path) throws IOException {
        try {
            log.debug("{}<-创建文件夹：{}", LOG_PREFIX, path);
            StatObjectResponse state = MinioUtils.getStat(client, properties.getBucket(), path);
            if (state == null) {
                log.debug("{}->创建成功：{}", LOG_PREFIX, path);
                MinioUtils.mkdir(client, properties.getBucket(), path);
                return true;
            } else {
                log.warn("{}->文件夹已存在：{}", LOG_PREFIX, path);
                return false;
            }
        } catch (Exception e) {
            throw MinioUtils.toIOException(e);
        }
    }

    @Override
    public long store(String path, long size, InputStream inputStream) throws IOException {
        try {
            StatObjectResponse stat = MinioUtils.getStat(client, properties.getBucket(), path);
            if(!MinioUtils.isDir(stat)) {
                client.putObject(PutObjectArgs.builder()
                        .bucket(properties.getBucket())
                        .object(MinioUtils.toMinioObjectName(path))
                        .stream(inputStream, size, 1024 * 1024 * 20)
                        .build()
                );
                return size;
            } else {
                throw new IOException(path + "为目录");
            }
        } catch (Exception e) {
            throw MinioUtils.toIOException(e);
        }
    }


    @Override
    public OutputStream newOutputStream(String path) throws IOException {

        // Minio不支持创建输出流，这里需要在本地创建临时文件，使用临时文件的输出流，流关闭时再存储到Minio
        Path tempPath = Paths.get(StringUtils.appendPath(PathUtils.getTempDirectory(), StringUtils.getRandomString(32)));
        OutputStream outputStream = Files.newOutputStream(tempPath);
        log.info("{}<-创建输出流：{}，本地临时文件：{}", LOG_PREFIX, path, tempPath);

        return new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                outputStream.write(b);
            }

            @Override
            public void write(byte[] b) throws IOException {
                outputStream.write(b);
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                outputStream.write(b, off, len);
            }

            @Override
            public void flush() throws IOException {
                outputStream.flush();
            }

            @Override
            public void close() throws IOException {
                log.info("{}->输出流关闭：{} 本地临时文件：{}", LOG_PREFIX, path, tempPath);
                outputStream.close();
                try(InputStream is = Files.newInputStream(tempPath)) {
                    store(path, Files.size(tempPath), is);
                }
                Files.deleteIfExists(tempPath);
                log.info("{}->删除临时文件：{}", LOG_PREFIX, tempPath);
            }
        };
    }

    @Override
    public boolean rename(String path, String newName) throws IOException {
        String dest = StringUtils.appendPath(PathUtils.getParentPath(path), newName);
        move(MinioUtils.toMinioObjectName(path), MinioUtils.toMinioObjectName(dest));
        return true;
    }

    @Override
    public boolean copy(String src, String dest) throws IOException {
        log.debug("{}<-执行复制：{} -> {}", LOG_PREFIX, src, dest);
        try {
            Iterable<Result<Item>> lists = client.listObjects(ListObjectsArgs.builder().bucket(properties.getBucket())
                    .prefix(MinioUtils.toDirectoryName(src))
                    .recursive(true)
                    .build());

            boolean isDir = false;
            for (Result<Item> list : lists) {
                isDir = true;
                Item item = list.get();
                String originName = item.objectName();
                String newName = StringUtils.appendPath(dest, originName.substring(src.length()));

                log.debug("{}->复制子项：{} -> {}", LOG_PREFIX, originName, newName);
                client.copyObject(CopyObjectArgs.builder()
                        .bucket(properties.getBucket())
                        .taggingDirective(Directive.COPY)
                        .source(CopySource.builder().bucket(properties.getBucket()).object(MinioUtils.toMinioObjectName(originName)).build())
                        .object(MinioUtils.toMinioObjectName(newName))
                        .build());
            }

            if (!isDir) {
                client.copyObject(CopyObjectArgs.builder()
                        .bucket(properties.getBucket())
                        .taggingDirective(Directive.COPY)
                        .source(CopySource.builder().bucket(properties.getBucket()).object(MinioUtils.toMinioObjectName(src)).build())
                        .object(MinioUtils.toMinioObjectName(dest))
                        .build());
            }
            return true;
        } catch (Exception e) {
            throw MinioUtils.toIOException(e);
        }
    }

    @Override
    public boolean move(String src, String dest) throws IOException {
        log.debug("{}<-文件移动：{} -> {}", LOG_PREFIX, src, dest);
        String actualSrc = src, actualDest = dest;

        // 若获取不到，则可能是目录
        StatObjectResponse stat = MinioUtils.getStat(client, properties.getBucket(), actualSrc);
        if (stat == null) {
            actualSrc = actualSrc + "/";
            actualDest = actualDest + "/";
            mkdir(actualDest);
        }

        copy(actualSrc, actualDest);
        delete(actualSrc);
        return true;
    }

    @Override
    public boolean mkdirs(String path) throws IOException {
        return mkdir(path);
    }
}
