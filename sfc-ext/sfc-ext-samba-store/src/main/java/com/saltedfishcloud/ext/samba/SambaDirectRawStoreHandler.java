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
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.SmbConfig;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.Directory;
import com.hierynomus.smbj.share.DiskShare;
import com.hierynomus.smbj.share.File;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.store.DirectRawStoreHandler;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;

@Slf4j
public class SambaDirectRawStoreHandler implements DirectRawStoreHandler {
    private static final String LOG_PREFIX = "[Samba]";
    private SambaProperty sambaProperty;
    private SMBClient client;
    private Session session;
    private DiskShare share;
    private static final Set<AccessMask> ACCESS_MASK_SET = new HashSet<>(List.of(AccessMask.GENERIC_ALL));
    public SambaDirectRawStoreHandler(SambaProperty property) {
        this.sambaProperty = property;
        this.client = new SMBClient(SmbConfig.createDefaultConfig());
    }

    protected synchronized Session getSession() throws IOException {
        if (session == null || !session.getConnection().isConnected()) {
            Integer port = Optional.ofNullable(sambaProperty.getPort()).orElse(445);
            Connection connect = client.connect(sambaProperty.getHost(), port);
            AuthenticationContext authenticationContext = new AuthenticationContext(
                    sambaProperty.getUsername(),
                    Optional.ofNullable(sambaProperty.getPassword()).orElse("").toCharArray(),
                    ""
            );
            this.session = connect.authenticate(authenticationContext);
        }
        return session;
    }

    protected synchronized DiskShare getShare() throws IOException {
        if (share == null || !share.isConnected()) {
            return (DiskShare) getSession().connectShare(sambaProperty.getShareName());
        } else {
            return share;
        }
    }

    @Override
    public boolean exist(String path) {
        try {
            if ("/".equals(path)) {
                return true;
            }
            boolean exists = getShare().fileExists(path);
            if (!exists) {
                exists = getShare().folderExists(path);
            }
            return exists;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    protected File openFileWrite(String path) throws IOException {
        return getShare().openFile(path, ACCESS_MASK_SET, null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OVERWRITE_IF, null);
    }

    protected File openFileRead(String path) throws IOException {
        return getShare().openFile(path, ACCESS_MASK_SET, null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null);
    }

    private Directory openDir(String path) throws IOException {
        return getShare().openDirectory(path, ACCESS_MASK_SET, null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null);
    }

    @Override
    public boolean isEmptyDirectory(String path) throws IOException {
        return listFiles(path).isEmpty();
    }

    @Override
    public Resource getResource(String path) throws IOException {
        FileInfo fileInfo = getFileInfo(path);
        if (fileInfo == null || fileInfo.isDir()) {
            return null;
        }
        File file = openFileRead(path);
        return new SambaResource(file);
    }

    @Override
    public List<FileInfo> listFiles(String path) throws IOException {
        List<FileIdBothDirectoryInformation> list = getShare().list(path);
        List<FileInfo> res = new ArrayList<>(list.size());
        for (FileIdBothDirectoryInformation info : list) {
            if (info.getFileName().equals(".") || info.getFileName().equals("..")) {
                continue;
            }
            res.add(convertToFileInfo(info));
        }
        return res;
    }

    private FileInfo convertToFileInfo(FileIdBothDirectoryInformation info) {
        FileInfo fileInfo = new FileInfo();
        if (info.getFileAttributes() == FileAttributes.FILE_ATTRIBUTE_DIRECTORY.getValue()) {
            fileInfo.setSize(-1);
            fileInfo.setType(FileInfo.TYPE_DIR);
        } else {
            fileInfo.setSize(info.getEndOfFile());
            fileInfo.setType(FileInfo.TYPE_FILE);
        }

        fileInfo.setLastModified(info.getLastWriteTime().toDate().getTime());
        fileInfo.setName(info.getFileName());
        fileInfo.setCreatedAt(info.getCreationTime().toDate());
        fileInfo.setUpdatedAt(info.getLastWriteTime().toDate());
        return fileInfo;
    }

    private FileInfo convertToFileInfo(FileAllInformation info) {
        FileInfo fileInfo = new FileInfo();
        FileBasicInformation baseInfo = info.getBasicInformation();
        boolean isDir = baseInfo.getFileAttributes() == FileAttributes.FILE_ATTRIBUTE_DIRECTORY.getValue();
        fileInfo.setLastModified(baseInfo.getLastWriteTime().toDate().getTime());
        fileInfo.setName(info.getNameInformation());
        fileInfo.setSize(isDir ? -1 : info.getStandardInformation().getEndOfFile());
        fileInfo.setType(isDir ? FileInfo.TYPE_DIR : FileInfo.TYPE_FILE);
        fileInfo.setCreatedAt(baseInfo.getCreationTime().toDate());
        fileInfo.setUpdatedAt(baseInfo.getLastWriteTime().toDate());
        return fileInfo;
    }


    @Override
    public FileInfo getFileInfo(String path) throws IOException {
        try {
            FileAllInformation fileInformation = getShare().getFileInformation(path);
            return convertToFileInfo(fileInformation);
        } catch (SMBApiException e) {
            e.printStackTrace();
            return null;
        }

    }

    @Override
    public boolean delete(String path) throws IOException {
        // 好像有bug，删除文件后如果不close掉share，该文件就一直保持着删除中导致无法新增同名文件或目录
        synchronized (DiskShare.class) {
            try {
                getShare().rm(path);
            } catch (SMBApiException e) {
                if(e.getStatusCode() == NtStatus.STATUS_FILE_IS_A_DIRECTORY.getValue()) {
                    if(!isEmptyDirectory(path)) {
                        List<FileInfo> fileInfos = listFiles(path);
                        for (FileInfo file : fileInfos) {
                            delete(StringUtils.appendPath(path, file.getName()));
                        }
                    }
                }
                getShare().rmdir(path, true);
            }
            getShare().close();
        }

        return true;
    }

    @Override
    public boolean mkdir(String path) throws IOException {
        log.debug("{}创建文件夹：{}", LOG_PREFIX, path);
        path = convertDelimiter(path);
        getShare().mkdir(path);
        return true;
    }

    @Override
    public boolean mkdirs(String path) throws IOException {
        getShare().mkdir(path);
        return true;
    }

    @Override
    public long store(String path, long size, InputStream inputStream) throws IOException {
        try(OutputStream os = newOutputStream(path)) {
            StreamUtils.copy(inputStream, os);
        }
        return size;
    }

    @Override
    public OutputStream newOutputStream(String path) throws IOException {
        return openFileWrite(path).getOutputStream();
    }

    @Override
    public boolean rename(String path, String newName) throws IOException {
        String newPath = StringUtils.appendPath(PathUtils.getParentPath(path), newName)
                .replaceAll("^[/\\\\]+", "")
                .replaceAll("/", "\\\\");
        path = convertDelimiter(path);
        FileInfo fileInfo = getFileInfo(path);
        if (fileInfo.isDir()) {
            openDir(path).rename(newPath, false);
        } else {
            File sourceFile = openFileRead(path);
            sourceFile.rename(newPath, false);
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
        try {
            openFileRead(src).remoteCopyTo(openFileWrite(dest));
        } catch (Buffer.BufferException e) {
            e.printStackTrace();
            throw new IOException(e);
        }
        return true;
    }

    @Override
    public boolean move(String src, String dest) throws IOException {
        openFileRead(src).rename(dest);
        return true;
    }
}
