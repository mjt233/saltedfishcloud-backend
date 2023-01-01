package com.saltedfishcloud.ext.sftp;

import com.saltedfishcloud.ext.sftp.config.SFTPProperty;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.store.DirectRawStoreHandler;
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
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.PublicKey;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class SFTPDirectRawStoreHandler implements DirectRawStoreHandler, Closeable {
    private final static String LOG_PREFIX = "[SFTP]";
    private final static EnumSet<OpenMode> CREATE_OPEN_MODE = EnumSet.of(OpenMode.WRITE, OpenMode.CREAT, OpenMode.TRUNC);

    @Getter
    private final SFTPProperty property;

    private final ObjectPool<SFTPSession> pool;

    public SFTPDirectRawStoreHandler(SFTPProperty property) {
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
                    .lastModified(fileAttributes.getMtime())
                    .size(fileAttributes.getSize())
                    .build();
        }

    }

    private FileInfo remoteResourceInfoToFileInfo(RemoteResourceInfo info) {
        FileAttributes attributes = info.getAttributes();
        Date mtime = new Date(attributes.getMtime());
        FileInfo fileInfo = new FileInfo();
        fileInfo.setCreatedAt(mtime);
        fileInfo.setMount(false);
        fileInfo.setLastModified(mtime.getTime());
        fileInfo.setUpdatedAt(mtime);
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

        Date mtime = new Date(attributes.getMtime());
        fileInfo.setUpdatedAt(mtime);
        fileInfo.setCreatedAt(mtime);
        fileInfo.setLastModified(mtime.getTime());
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
    public long store(String path, long size, InputStream inputStream) throws IOException {
        FileInfo fileInfo = getFileInfo(path);
        if (fileInfo != null) {
            if (fileInfo.isDir()) {
                throw new IOException(path + "是目录，无法覆盖");
            }
        }
        try (SFTPClient sftpClient = getSFTPClient()) {
            try(RemoteFile file = sftpClient.open(path, CREATE_OPEN_MODE)) {
                try(RemoteFile.RemoteFileOutputStream os = file.new RemoteFileOutputStream()) {
                    return StreamUtils.copy(inputStream, os);
                }
            }
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
    public boolean copy(String src, String dest) throws IOException {
        Resource srcResource = getResource(src);
        try(OutputStream os = newOutputStream(dest)) {
            try(InputStream is = srcResource.getInputStream()) {
                StreamUtils.copy(is, os);
            }
        }
        return true;
    }

    @Override
    public boolean move(String src, String dest) throws IOException {
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
}
