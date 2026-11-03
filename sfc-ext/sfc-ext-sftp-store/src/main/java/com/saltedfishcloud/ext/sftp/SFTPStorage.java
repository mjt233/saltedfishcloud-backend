package com.saltedfishcloud.ext.sftp;

import com.saltedfishcloud.ext.sftp.config.SFTPProperty;
import com.xiaotao.saltedfishcloud.model.param.FileTimeAttribute;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.model.progress.FileTransferItem;
import com.xiaotao.saltedfishcloud.service.file.store.Storage;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.PoolUtils;
import com.sfc.common.utils.SfcStringUtils;
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
                            .map(e -> SfcStringUtils.appendPath(path, e.getName()))
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
            sftpClient.mkdirs(path);
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
        SFTPClient client = getSFTPClient();
        RemoteFile file;
        try {
            // TRUNC 必须与 CREAT 同用：目标文件不存在时缺少 CREAT 会直接报 SFTPException: No such file
            file = client.open(path, CREATE_OPEN_MODE);
        } catch (IOException | RuntimeException e) {
            // 打开文件失败时需要先把借出的会话归还连接池，避免会话泄漏
            try {
                client.close();
            } catch (IOException closeException) {
                e.addSuppressed(closeException);
            }
            throw e;
        }
        return new SFTPFileOutputStream(file, client);
    }

    @Override
    public boolean rename(String path, String newName) throws IOException {
        try (SFTPClient sftpClient = getSFTPClient()) {
            sftpClient.rename(path, SfcStringUtils.appendPath(PathUtils.getParentPath(path), newName));
        }

        return true;
    }

    @Override
    public boolean copy(String src, String dest, @Nullable FileTransferItem item) throws IOException {
        Resource srcResource = getResource(src);
        if (srcResource == null) {
            throw new IOException("复制源文件不存在：" + src);
        }
        // 先打开源文件再创建目标文件，避免源文件不可读时目标文件已被创建或被截断
        try (InputStream is = srcResource.getInputStream(); OutputStream os = newOutputStream(dest)) {
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
            renameWithOverwrite(sftpClient, src, dest);
        }
        return true;
    }

    /**
     * 支持覆盖已存在普通文件的重命名/移动。
     * <p>
     * OpenSSH 的 SFTP 服务端只实现协议 v3，其 RENAME 请求不支持覆盖语义（服务端以 link()+unlink() 实现，
     * 目标已存在时返回 SSH_FX_FAILURE，映射为 {@link Response.StatusCode#FAILURE}）；
     * sshj 仅对 SFTP v5+ 下发 RenameFlags，对 OpenSSH(v3) 无效，且未实现 posix-rename@openssh.com 扩展，
     * 因此这里采用「删除已存在的目标普通文件后重试 rename」的方式实现覆盖。
     * <p>
     * 仅在确认源与目标都是存在的普通文件时才执行删除重试；源丢失、目标不存在、任一方为目录或其他错误码时
     * 原样抛出原始异常，不删除任何内容，避免误删目录或在源已丢失时误删目标，行为符合 {@link Storage#move} 的失败语义。
     * <p>
     * 另外，源与目标为同一路径时直接视为移动成功（no-op）：OpenSSH v3 对 rename(p, p) 同样返回 FAILURE，
     * 若不拦截，后续的删除重试会先删掉该文件唯一的目录项，造成源文件永久丢失。
     *
     * @param sftpClient 已借出的SFTP会话
     * @param src        源路径
     * @param dest       目标路径
     * @throws IOException 重命名失败（包括无法覆盖的非普通文件场景）时抛出
     */
    private void renameWithOverwrite(SFTPClient sftpClient, String src, String dest) throws IOException {
        try {
            sftpClient.rename(src, dest);
        } catch (SFTPException e) {
            if (e.getStatusCode() != Response.StatusCode.FAILURE) {
                throw e;
            }
            // 同路径移动为 no-op 成功，绝不能进入删除重试逻辑，否则会删掉源文件
            if (src.equals(dest)) {
                return;
            }
            FileAttributes destAttributes = sftpClient.statExistence(dest);
            FileAttributes srcAttributes = sftpClient.statExistence(src);
            if (destAttributes == null || srcAttributes == null
                    || destAttributes.getType() == FileMode.Type.DIRECTORY
                    || srcAttributes.getType() == FileMode.Type.DIRECTORY) {
                throw e;
            }
            // 目标已存在且均为普通文件：SFTP v3 的 RENAME 无法覆盖，删除目标后重试
            sftpClient.rm(dest);
            sftpClient.rename(src, dest);
        }
    }

    @Override
    public boolean mkdirs(String path) throws IOException {
        try (SFTPClient sftpClient = getSFTPClient()) {
            sftpClient.mkdirs(path);
        }

        return true;
    }

    @Override
    public boolean exist(String path) throws IOException {
        try (SFTPClient sftpClient = getSFTPClient()) {
            return sftpClient.statExistence(path) != null;
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
                String fullPath = SfcStringUtils.appendPath(path, name);
                
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
