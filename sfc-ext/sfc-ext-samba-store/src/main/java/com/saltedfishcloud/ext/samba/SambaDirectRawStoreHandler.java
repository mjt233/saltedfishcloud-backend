package com.saltedfishcloud.ext.samba;

import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.mserref.NtStatus;
import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.msfscc.fileinformation.FileAllInformation;
import com.hierynomus.msfscc.fileinformation.FileBasicInformation;
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.mssmb2.SMBApiException;
import com.hierynomus.protocol.commons.buffer.Buffer;
import com.hierynomus.protocol.transport.TransportException;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.SmbConfig;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.Directory;
import com.hierynomus.smbj.share.DiskShare;
import com.hierynomus.smbj.share.File;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.model.param.FileTimeAttribute;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.model.progress.FileTransferItem;
import com.xiaotao.saltedfishcloud.service.file.store.DirectRawStoreHandler;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.PoolUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
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
import java.nio.file.FileAlreadyExistsException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Samba文件共享存储操作器
 */
@Slf4j
public class SambaDirectRawStoreHandler implements DirectRawStoreHandler, Closeable {
    private static final String LOG_PREFIX = "[Samba]";
    @Getter
    private final SambaProperty sambaProperty;

    private final SMBClient client;
    private final ObjectPool<Session> sessionObjectPool;
    private static final Set<AccessMask> ACCESS_MASK_SET = new HashSet<>(List.of(AccessMask.GENERIC_ALL));
    public SambaDirectRawStoreHandler(SambaProperty property) {
        this.sambaProperty = property;
        this.client = new SMBClient(SmbConfig.createDefaultConfig());
        sessionObjectPool = PoolUtils.createObjectPool(new BasePooledObjectFactory<Session>() {
            @Override
            public Session create() throws Exception {
                log.debug("{}创建新smb session", LOG_PREFIX);
                Integer port = Optional.ofNullable(sambaProperty.getPort()).orElse(445);
                Connection connect = client.connect(sambaProperty.getHost(), port);
                AuthenticationContext authenticationContext = new AuthenticationContext(
                        sambaProperty.getUsername(),
                        Optional.ofNullable(sambaProperty.getPassword()).orElse("").toCharArray(),
                        ""
                );
                return connect.authenticate(authenticationContext);
            }

            @Override
            public PooledObject<Session> wrap(Session session) {
                return new DefaultPooledObject<>(session);
            }

            @Override
            public void destroyObject(PooledObject<Session> p) throws Exception {
                p.getObject().close();
            }

            @Override
            public boolean validateObject(PooledObject<Session> p) {
                return p.getObject().getConnection().isConnected();
            }
        });
    }

    @Override
    public void close() {
        try {
            sessionObjectPool.close();
        } finally {
            client.close();
        }
    }

    @Override
    public boolean exist(String path) {
        if ("/".equals(path)) {
            return true;
        }
        return getFileInfo(path) != null;
    }

    public void returnSession(Session session) {
        try {
            log.debug("{}smb session归还对象池", LOG_PREFIX);
            sessionObjectPool.returnObject(session);
        } catch (Exception e) {
            log.debug("{}smb session归还对象池出错", LOG_PREFIX, e);
        }
    }

    public Session getSession() {
        try {
            return sessionObjectPool.borrowObject();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void getDiskShare(Consumer<DiskShare> shareConsumer) {
        try {
            Session session = getSession();
            try (DiskShare share = (DiskShare) session.connectShare(sambaProperty.getShareName())){
                shareConsumer.accept(share);
            } finally {
                returnSession(session);
            }
        } catch (Exception e) {
            log.error("{}", LOG_PREFIX, e);
            throw new JsonException(e.getMessage());
        }
    }

    public <T> T getDiskShare(Function<DiskShare, T> shareConsumer) {
        AtomicReference<T> reference = new AtomicReference<>();
        getDiskShare(diskShare -> {
            reference.set(shareConsumer.apply(diskShare));
        });
        return reference.get();
    }

    public File openFileWrite(DiskShare share, String path) {
        return share.openFile(convertDelimiter(path), ACCESS_MASK_SET, null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OVERWRITE_IF, null);
    }

    public File openFileRead(DiskShare share, String path) {
        return share.openFile(convertDelimiter(path), ACCESS_MASK_SET, null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null);
    }

    @Override
    public boolean isEmptyDirectory(String path) {
        return listFiles(path).isEmpty();
    }

    @Override
    public Resource getResource(String path) throws IOException {
        FileInfo fileInfo = getFileInfo(path);
        if (fileInfo == null || fileInfo.isDir()) {
            return null;
        }
        return new SambaResource(this, path, fileInfo.getName(), fileInfo.getMtime(), convertDelimiter(path));
    }

    @Override
    public List<FileInfo> listFiles(String path) {
        return getDiskShare(diskShare -> {
            List<FileIdBothDirectoryInformation> list = diskShare.list(convertDelimiter(path));
            List<FileInfo> res = new ArrayList<>(list.size());
            for (FileIdBothDirectoryInformation info : list) {
                if (info.getFileName().equals(".") || info.getFileName().equals("..")) {
                    continue;
                }
                res.add(convertToFileInfo(info));
            }
            return res;
        });
    }

    private FileInfo convertToFileInfo(FileIdBothDirectoryInformation info) {
        FileInfo fileInfo = new FileInfo();
        if (isDirectory(info.getFileAttributes())) {
            fileInfo.setSize(-1L);
            fileInfo.setType(FileInfo.TYPE_DIR);
        } else {
            fileInfo.setSize(info.getEndOfFile());
            fileInfo.setType(FileInfo.TYPE_FILE);
        }

        fileInfo.setMtime(info.getLastWriteTime().toDate().getTime());
        fileInfo.setName(info.getFileName());
        fileInfo.setCreateAt(info.getCreationTime().toDate());
        fileInfo.setUpdateAt(info.getLastWriteTime().toDate());
        return fileInfo;
    }

    private FileInfo convertToFileInfo(FileAllInformation info) {
        FileInfo fileInfo = new FileInfo();
        FileBasicInformation baseInfo = info.getBasicInformation();
        boolean isDir = isDirectory(baseInfo.getFileAttributes());
        fileInfo.setMtime(baseInfo.getLastWriteTime().toDate().getTime());
        fileInfo.setName(info.getNameInformation());
        fileInfo.setSize(isDir ? -1 : info.getStandardInformation().getEndOfFile());
        fileInfo.setType(isDir ? FileInfo.TYPE_DIR : FileInfo.TYPE_FILE);
        fileInfo.setCreateAt(baseInfo.getCreationTime().toDate());
        fileInfo.setUpdateAt(baseInfo.getLastWriteTime().toDate());
        return fileInfo;
    }


    @Override
    public FileInfo getFileInfo(String path) {
        return getDiskShare(diskShare -> {
            try {
                FileAllInformation fileInformation = diskShare.getFileInformation(convertDelimiter(path));
                return convertToFileInfo(fileInformation);
            } catch (SMBApiException ignore) {
                return null;
            }
        });
    }

    @Override
    public boolean delete(String path) {
        getDiskShare((Consumer<DiskShare>) diskShare -> delete(diskShare, convertDelimiter(path)));
        return true;
    }

    private void delete(DiskShare diskShare, String path) {
        Deque<String> pending = new ArrayDeque<>();
        Deque<String> directories = new ArrayDeque<>();
        pending.push(path);

        while (!pending.isEmpty()) {
            String currentPath = pending.pop();
            try {
                diskShare.rm(currentPath);
            } catch (SMBApiException e) {
                if (e.getStatusCode() == NtStatus.STATUS_FILE_IS_A_DIRECTORY.getValue()) {
                    // 删除非空文件夹时会抛出这个异常，此时需要先删除文件夹内的内容
                    directories.push(currentPath);
                    List<FileIdBothDirectoryInformation> fileInfos = diskShare.list(currentPath);
                    for (FileIdBothDirectoryInformation file : fileInfos) {
                        String fileName = file.getFileName();
                        if (".".equals(fileName) || "..".equals(fileName)) {
                            continue;
                        }
                        pending.push(convertDelimiter(StringUtils.appendPath(currentPath, fileName)));
                    }
                    continue;
                }

                if (e.getStatusCode() == NtStatus.STATUS_OBJECT_NAME_NOT_FOUND.getValue()) {
                    // 文件已经不存在了，跳过
                    continue;
                }

                // 其他错误直接抛出异常
                throw e;
            }
        }

        // 删除上面遍历期间遇到的所有文件夹，经过删除文件后，这些文件夹都是空文件夹，可以直接删除了
        while (!directories.isEmpty()) {
            String directoryPath = directories.pop();
            try {
                diskShare.rmdir(directoryPath, true);
            } catch (SMBApiException e) {
                if (e.getStatusCode() != NtStatus.STATUS_OBJECT_NAME_NOT_FOUND.getValue()) {
                    // 文件已经不存在了，跳过
                    continue;
                }
                throw e;
            }
        }
    }

    @Override
    public boolean mkdir(String path) {
        log.debug("{}创建文件夹：{}", LOG_PREFIX, path);
        getDiskShare((Consumer<DiskShare>) diskShare -> diskShare.mkdir(convertDelimiter(path)));
        return true;
    }

    @Override
    public boolean mkdirs(String path) {
        return this.mkdir(path);
    }

    @Override
    public long store(FileInfo fileInfo, String path, long size, InputStream inputStream) throws IOException {
        try(OutputStream os = newOutputStream(path)) {
            StreamUtils.copy(inputStream, os);
        }
        return size;
    }

    @Override
    public OutputStream newOutputStream(String path) throws IOException {
        Session session = getSession();
        DiskShare diskShare = null;
        File file = null;
        OutputStream originOutputStream = null;
        boolean success = false;
        try {
            diskShare = (DiskShare) session.connectShare(sambaProperty.getShareName());
            file = openFileWrite(diskShare, path);
            originOutputStream = file.getOutputStream();
            success = true;
        } finally {
            if (!success) {
                closeQuietly(originOutputStream);
                closeQuietly(file);
                closeQuietly(diskShare);
                returnSession(session);
            }
        }

        File openedFile = file;
        DiskShare openedShare = diskShare;
        OutputStream openedOutputStream = originOutputStream;
        return com.xiaotao.saltedfishcloud.utils.StreamUtils.createCloseActionOutputStream(openedOutputStream, () -> {
            try {
                closeQuietly(openedFile);
            } finally {
                try {
                    closeQuietly(openedShare);
                } finally {
                    returnSession(session);
                }
            }
        });
    }

    @Override
    public boolean rename(String path, String newName) throws IOException {
        String newPath = StringUtils.appendPath(PathUtils.getParentPath(path), newName)
                .replaceAll("^[/\\\\]+", "")
                .replaceAll("/", "\\\\");
        String smbPath = convertDelimiter(path);
        renameInternal(smbPath, newPath);
        return true;
    }

    private void renameInternal(String sourcePath, String targetPath) {
        String smbPath = convertDelimiter(sourcePath);
        String smbTargetPath = convertDelimiter(targetPath)
                .replaceAll("^[/\\\\]+", "");
        getDiskShare(share -> {
            try {
                FileAllInformation fileInformation = share.getFileInformation(smbPath);
                boolean isDirectory = isDirectory(fileInformation.getBasicInformation().getFileAttributes());
                if (isDirectory) {
                    try (Directory directory = share.openDirectory(smbPath, ACCESS_MASK_SET, null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null)) {
                        directory.rename(smbTargetPath, false);
                    }
                } else {
                    try (File sourceFile = openFileRead(share, smbPath)) {
                        sourceFile.rename(smbTargetPath, false);
                    }
                }
            } catch (SMBApiException e) {
                if (e.getStatusCode() == NtStatus.STATUS_OBJECT_NAME_COLLISION.getValue()) {
                    throw new RuntimeException(new FileAlreadyExistsException(smbTargetPath));
                }
                throw e;
            }
        });
    }


    /**
     * 将请求路径转换为smb协议的反斜杠分隔符形式
     */
    private String convertDelimiter(String path) {
        return path.replaceAll("/+", "\\\\");
    }

    @Override
    public boolean copy(String src, String dest, @Nullable FileTransferItem item) throws IOException {
        getDiskShare(share -> {
            try (File sourceFile = openFileRead(share, src); File targetFile = openFileWrite(share, dest)) {
                sourceFile.remoteCopyTo(targetFile);
            } catch (Buffer.BufferException | TransportException e) {
                throw new RuntimeException(e);
            }
        });
        return true;
    }

    @Override
    public boolean move(String src, String dest, @Nullable FileTransferItem item) throws IOException {
        renameInternal(src, dest);
        return true;
    }

    @Override
    public void updateTime(String path, List<String> names, FileTimeAttribute attribute) throws IOException {

    }

    private void closeQuietly(@Nullable AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception e) {
            log.debug("{}关闭资源失败 {} ", LOG_PREFIX, closeable, e);
        }
    }

    private boolean isDirectory(long fileAttributes) {
        return (fileAttributes & FileAttributes.FILE_ATTRIBUTE_DIRECTORY.getValue()) != 0;
    }
}
