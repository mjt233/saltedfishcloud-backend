package com.saltedfishcloud.ext.sftp;

import com.saltedfishcloud.ext.sftp.config.SFTPProperty;
import com.xiaotao.saltedfishcloud.model.param.FileTimeAttribute;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.model.progress.FileTransferItem;
import com.xiaotao.saltedfishcloud.service.file.store.Storage;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.PoolUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.*;
import net.schmizz.sshj.transport.verification.HostKeyVerifier;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.PublicKey;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class SFTPStorage implements Storage, Closeable {
    private final static String LOG_PREFIX = "[SFTP]";
    private final static EnumSet<OpenMode> CREATE_OPEN_MODE = EnumSet.of(OpenMode.WRITE, OpenMode.CREAT, OpenMode.TRUNC);

    @Getter
    private final SFTPProperty property;

    private final ObjectPool<SFTPSession> pool;

    public SFTPStorage(SFTPProperty property) {
        this.property = property;
        pool = PoolUtils.createObjectPool(new BasePooledObjectFactory<>() {
            @Override
            public SFTPSession create() throws Exception {
                log.debug("{}创建SFTP会话", LOG_PREFIX);
                return new SFTPSession(getSSHClient(), pool);
            }

            @Override
            public PooledObject<SFTPSession> wrap(SFTPSession sftpSession) {
                return new DefaultPooledObject<>(sftpSession);
            }

            @Override
            public boolean validateObject(PooledObject<SFTPSession> p) {
                try {
                    return p.getObject().statExistence(property.getPath()) != null;
                } catch (IOException e) {
                    log.error("{}会话失效：{}", LOG_PREFIX, e);
                    return false;
                }
            }

            @Override
            public void destroyObject(PooledObject<SFTPSession> p) throws Exception {
                log.debug("{}SFTP会话销毁", LOG_PREFIX);
                p.getObject().closeSession();
            }
        });
    }

    public SSHClient getSSHClient() throws IOException {
        SSHClient ssh = new SSHClient();
        ssh.addHostKeyVerifier(new HostKeyVerifier() {
            @Override
            public boolean verify(String hostname, int port, PublicKey key) {
                return true;
            }

            @Override
            public List<String> findExistingAlgorithms(String hostname, int port) {
                return Collections.emptyList();
            }
        });
        ssh.connect(property.getHost(), property.getPort());
        ssh.authPassword(property.getUsername(), property.getPassword());
        return ssh;
    }

    public SFTPClient getSFTPClient() throws IOException {
        try {
            return pool.borrowObject();
        } catch (Exception e) {
            log.error("{}获取客户端错误：{}", LOG_PREFIX, e);
            throw new IOException(e);
        }
    }

    @Override
    public boolean isEmptyDirectory(String path) throws IOException {
        try(SFTPClient sftpClient = getSFTPClient()) {
            List<RemoteResourceInfo> ls = sftpClient.ls(path);
            if (ls == null) {
                throw new IOException(path + " 不存在");
            }
            return ls.isEmpty();
        }
    }

    @Override
    public Resource getResource(String path) throws IOException {
        try(SFTPClient sftpClient = getSFTPClient()) {

            FileAttributes fileAttributes = sftpClient.statExistence(path);
            if (fileAttributes == null) {
                return null;
            }
            if(fileAttributes.getType() == FileMode.Type.DIRECTORY) {
                return null;
            }

            return SFTPResource.builder()
                    .handler(this)
                    .path(path)
                    .name(PathUtils.getLastNode(path))
                    .lastModified(fileAttributes.getMtime() * 1000)
                    .size(fileAttributes.getSize())
                    .build();
        }

    }

    private FileInfo remoteResourceInfoToFileInfo(RemoteResourceInfo info) {
        FileAttributes attributes = info.getAttributes();
        FileInfo fileInfo = new FileInfo();
        fileInfo.setMtime(attributes.getMtime() * 1000);
        fileInfo.setIsMount(false);
        fileInfo.setName(info.getName());
        fileInfo.setType(info.isDirectory() ? FileInfo.TYPE_DIR : FileInfo.TYPE_FILE);
        fileInfo.setSize(info.isDirectory() ? -1 : attributes.getSize());
        return fileInfo;
    }

    private FileInfo attributeToFileInfo(FileAttributes attributes) {
        FileInfo fileInfo = new FileInfo();
        boolean isDir = attributes.getType() == FileMode.Type.DIRECTORY;
        fileInfo.setSize(isDir ? -1 : attributes.getSize());
        fileInfo.setType(isDir ? FileInfo.TYPE_DIR : FileInfo.TYPE_FILE);
        fileInfo.setMtime(attributes.getMtime() * 1000);
        return fileInfo;
    }

    @Override
    public List<FileInfo> listFiles(String path) throws IOException {
        try(SFTPClient sftpClient = getSFTPClient()) {
            List<RemoteResourceInfo> ls = sftpClient.ls(path);
            if (ls == null) {
                return Collections.emptyList();
            }
            return ls.stream()
                    .map(this::remoteResourceInfoToFileInfo)
                    .collect(Collectors.toList());
        }
    }

    @Override
    public FileInfo getFileInfo(String path) throws IOException {
        try(SFTPClient sftpClient = getSFTPClient()) {

            FileAttributes fileAttributes = sftpClient.statExistence(path);
            if (fileAttributes == null) {
                return null;
            }
            FileInfo fileInfo = attributeToFileInfo(fileAttributes);
            fileInfo.setName(PathUtils.getLastNode(path));
            return fileInfo;
        }
    }

    @Override
    public boolean delete(String path) throws IOException {
        FileInfo fileInfo = getFileInfo(path);
        if (fileInfo == null) {
            log.warn("{}无效路径:{}", LOG_PREFIX, path);
            return false;
        }
        try(SFTPClient client = getSFTPClient()) {

            if (fileInfo.isFile()) {
                client.rm(path);
            } else {

                List<RemoteResourceInfo> ls = client.ls(path);
                if (ls == null) {
                    throw new IOException(path + " 不存在");
                }

                if(ls.isEmpty()) {
                    client.rmdir(path);
                } else {
                    List<FileInfo> fileInfos = listFiles(path);
                    List<String> subPaths = fileInfos.stream()
                            .map(e -> StringUtils.appendPath(path, e.getName()))
                            .collect(Collectors.toList());
                    for (String subPath : subPaths) {
                        delete(subPath);
                    }
                    client.rmdir(path);
                }
            }
            return true;
        }
    }

    @Override
    public boolean mkdir(String path) throws IOException {
        try (SFTPClient sftpClient = getSFTPClient()) {
            getSFTPClient().mkdirs(path);
            return true;
        }
    }

    @Override
    public long store(FileInfo fileInfo, String path, long size, InputStream inputStream) throws IOException {
        FileInfo existFile = getFileInfo(path);
        if (existFile != null) {
            if (existFile.isDir()) {
                throw new IOException(path + "是目录，无法覆盖");
            }
        }
        try (SFTPClient sftpClient = getSFTPClient();
             RemoteFile file = sftpClient.open(path, CREATE_OPEN_MODE);
             RemoteFile.RemoteFileOutputStream os = file.new RemoteFileOutputStream()
        ) {
            int ret = StreamUtils.copy(inputStream, os);
            os.close();
            if (fileInfo.getMtime() != null) {
                FileAttributes attributes = new FileAttributes.Builder()
                        .withAtimeMtime(System.currentTimeMillis() / 1000, fileInfo.getMtime() / 1000)
                        .build();
                file.setAttributes(attributes);
            }
            return ret;
        }
    }

    @Override
    public OutputStream newOutputStream(String path) throws IOException {
        try(SFTPClient client = getSFTPClient()) {
            return new SFTPFileOutputStream(client.open(path));
        }
    }

    @Override
    public boolean rename(String path, String newName) throws IOException {
        try (SFTPClient sftpClient = getSFTPClient()) {
            sftpClient.rename(path, StringUtils.appendPath(PathUtils.getParentPath(path), newName));
        }

        return true;
    }

    @Override
    public boolean copy(String src, String dest, @Nullable FileTransferItem item) throws IOException {
        Resource srcResource = getResource(src);
        try (OutputStream os = newOutputStream(dest);InputStream is = srcResource.getInputStream()) {
            if (item != null) {
                long total = srcResource.contentLength();
                item.setTotal(total);
                item.setLoaded(0L);
                com.xiaotao.saltedfishcloud.utils.StreamUtils.copyStream(is, os, (buf, len) -> {
                    item.setLoaded(item.getLoaded() + len);
                });
            } else {
                StreamUtils.copy(is, os);
            }
        }
        return true;
    }

    @Override
    public boolean move(String src, String dest, @Nullable FileTransferItem item) throws IOException {
        try (SFTPClient sftpClient = getSFTPClient()) {
            sftpClient.rename(src, dest);
        }
        return true;
    }

    @Override
    public boolean mkdirs(String path) throws IOException {
        try (SFTPClient sftpClient = getSFTPClient()) {
            sftpClient.mkdirs(path);
        }

        return true;
    }

    @Override
    public boolean exist(String path) {
        try {
            return getSFTPClient().statExistence(path) != null;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws IOException {
        try {
            pool.clear();
        } catch (Exception e) {
            log.error("SFTP连接池清空异常：", e);
        }
        pool.close();
    }

    @Override
    public void updateTime(String path, List<String> names, FileTimeAttribute attribute) throws IOException {
        try (SFTPClient client = getSFTPClient()) {
            for (String name : names) {
                String fullPath = StringUtils.appendPath(path, name);
                
                // 获取当前文件属性
                FileAttributes currentAttr = client.statExistence(fullPath);
                if (currentAttr == null) {
                    log.warn("{}文件不存在，跳过时间更新: {}", LOG_PREFIX, fullPath);
                    continue;
                }
                
                // 构建新的文件属性
                FileAttributes.Builder attrBuilder = new FileAttributes.Builder();
                
                // 设置访问时间和修改时间
                long atime = currentAttr.getAtime();
                long mtime = currentAttr.getMtime();
                
                // 如果提供了修改时间，则使用提供的值
                if (attribute.getModifyTime() != null) {
                    mtime = attribute.getModifyTime().getTime() / 1000;
                }
                
                // 如果提供了访问时间，则使用提供的值
                if (attribute.getLastAccessTime() != null) {
                    atime = attribute.getLastAccessTime().getTime() / 1000;
                }
                
                // 如果提供了创建时间，也设置修改时间（SFTP通常不单独支持创建时间）
                if (attribute.getCreateTime() != null && attribute.getModifyTime() == null) {
                    mtime = attribute.getCreateTime().getTime() / 1000;
                }
                
                // 构建并设置属性
                FileAttributes newAttr = attrBuilder
                        .withAtimeMtime(atime, mtime)
                        .build();
                
                client.setattr(fullPath, newAttr);
            }
        }
    }
}
