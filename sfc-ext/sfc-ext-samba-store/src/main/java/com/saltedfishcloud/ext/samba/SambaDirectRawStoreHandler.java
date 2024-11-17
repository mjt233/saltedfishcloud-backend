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
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
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
import org.jetbrains.annotations.NotNull;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
        sessionObjectPool.close();
    }

    @Override
    public boolean exist(String path) {
        if ("/".equals(path)) {
            return true;
        }
        return getDiskShare(share -> {
            boolean exists = share.fileExists(path);
            if (!exists) {
                exists = share.folderExists(path);
            }
            return exists;
        });
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
        return share.openFile(path, ACCESS_MASK_SET, null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OVERWRITE_IF, null);
    }

    public File openFileRead(DiskShare share, String path) {
        return share.openFile(path, ACCESS_MASK_SET, null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null);
    }

    public Directory openDir(String path) {
        return getDiskShare(diskShare -> {
            return diskShare.openDirectory(path, ACCESS_MASK_SET, null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null);
        });
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
        return new SambaResource(this, path);
    }

    @Override
    public List<FileInfo> listFiles(String path) {
        return getDiskShare(diskShare -> {
            List<FileIdBothDirectoryInformation> list = diskShare.list(path);
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
        if (info.getFileAttributes() == FileAttributes.FILE_ATTRIBUTE_DIRECTORY.getValue()) {
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
        boolean isDir = baseInfo.getFileAttributes() == FileAttributes.FILE_ATTRIBUTE_DIRECTORY.getValue();
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
                FileAllInformation fileInformation = diskShare.getFileInformation(path);
                return convertToFileInfo(fileInformation);
            } catch (SMBApiException e) {
                e.printStackTrace();
                return null;
            }
        });
    }

    @Override
    public boolean delete(String path) {
        getDiskShare(diskShare -> {
            try {
                diskShare.rm(path);
            } catch (SMBApiException e) {
                if(e.getStatusCode() == NtStatus.STATUS_FILE_IS_A_DIRECTORY.getValue()) {
                    if(!isEmptyDirectory(path)) {
                        List<FileInfo> fileInfos = listFiles(path);
                        for (FileInfo file : fileInfos) {
                            delete(StringUtils.appendPath(path, file.getName()));
                        }
                    }
                }
                diskShare.rmdir(path, true);
            }
        });
        return true;
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
        DiskShare diskShare = (DiskShare) session.connectShare(sambaProperty.getShareName());
        OutputStream originOutputStream = openFileWrite(diskShare, path).getOutputStream();
        return new OutputStream() {
            private boolean closed = false;
            @Override
            public void write(@NotNull byte[] b) throws IOException {
                originOutputStream.write(b);
            }

            @Override
            public void write(@NotNull byte[] b, int off, int len) throws IOException {
                originOutputStream.write(b, off, len);
            }

            @Override
            public void flush() throws IOException {
                originOutputStream.flush();
            }

            @Override
            public void close() throws IOException {
                if (closed) {
                    return;
                }
                originOutputStream.close();
                try {
                    diskShare.close();
                } finally {
                    returnSession(session);
                }
            }

            @Override
            public void write(int b) throws IOException {
                originOutputStream.write(b);
            }
        };
    }

    @Override
    public boolean rename(String path, String newName) throws IOException {
        String newPath = StringUtils.appendPath(PathUtils.getParentPath(path), newName)
                .replaceAll("^[/\\\\]+", "")
                .replaceAll("/", "\\\\");
        String smbPath = convertDelimiter(path);
        FileInfo fileInfo = getFileInfo(smbPath);
        if (fileInfo.isDir()) {
            openDir(smbPath).rename(newPath, false);
        } else {
            getDiskShare(share -> {
                File sourceFile = openFileRead(share, smbPath);
                sourceFile.rename(newPath, false);
            });
        }
        return true;
    }


    /**
     * 将请求的相对分享目录的路径转为完整的smb协议url格式的路径。
     * 如： /我的文件夹/文件1.jpg -> \\192.168.2.233\share\我的文件夹\文件1.jpg
     * @param path 请求的路径
     */
    private String toFullShareUrl(String path) {
        return "\\" + StringUtils.appendPath(sambaProperty.getHost(), sambaProperty.getShareName(), path)
                .replaceAll("/+", "\\\\");
    }

    /**
     * 将请求路径转换为smb协议的反斜杠分隔符形式
     */
    private String convertDelimiter(String path) {
        return path.replaceAll("/+", "\\\\");
    }

    @Override
    public boolean copy(String src, String dest) throws IOException {
        getDiskShare(share -> {
            try {
                openFileRead(share, src).remoteCopyTo(openFileWrite(share, dest));
            } catch (Buffer.BufferException | TransportException e) {
                throw new RuntimeException(e);
            }
        });
        return true;
    }

    @Override
    public boolean move(String src, String dest) throws IOException {
        getDiskShare(share -> {
            openFileRead(share, src).rename(dest);
        });
        return true;
    }
}
