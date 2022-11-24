package com.saltedfishcloud.ext.ftp;

import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.store.DirectRawStoreHandler;
import com.xiaotao.saltedfishcloud.utils.PoolUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.net.ftp.parser.ParserInitializationException;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.springframework.core.io.Resource;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.UnknownServiceException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class FTPDirectRawStoreHandler implements DirectRawStoreHandler, Closeable {
    private final static String LOG_PREFIX = "[FTP Client]";
    private final ObjectPool<FTPSession> pool;

    public FTPDirectRawStoreHandler(FTPProperty property) {
        pool = PoolUtils.createObjectPool(new BasePooledObjectFactory<>() {
            @Override
            public FTPSession create() throws Exception {
                log.debug("{}开始创建FTP会话", LOG_PREFIX);
                FTPSession client = new FTPSession(pool);
                client.setConnectTimeout(1000);
                client.setDataTimeout(1000);
                client.setConnectTimeout(1000);
                client.setDefaultTimeout(5000);
                client.connect(property.getHostname(), property.getPort());
                if(!client.login(property.getUsername(), property.getPassword())) {
                    client.disconnect();
                    throw new IOException("登录失败");
                }
                client.setFileType(FTPClient.BINARY_FILE_TYPE);
                client.setControlEncoding("UTF-8");
                client.setBufferSize(8192);
                if (property.isUsePassive()) {
                    client.enterLocalPassiveMode();
                }

                int replyCode = client.getReplyCode();
                if (!FTPReply.isPositiveCompletion(replyCode)) {
                    client.disconnect();
                    throw new IOException("FTP连接错误，错误码：" + replyCode);
                }
                if (!client.changeWorkingDirectory( "\\")) {
                    throw new IOException("FTP目录切换失败：" + property.getPath());
                }

                log.debug("{}创建FTP会话完成:{}", LOG_PREFIX, client.hashCode());
                return client;
            }

            @Override
            public PooledObject<FTPSession> wrap(FTPSession obj) {
                return new DefaultPooledObject<>(obj);
            }

            @Override
            public void destroyObject(PooledObject<FTPSession> p) throws Exception {
                log.debug("{}FTP会话销毁：{}", LOG_PREFIX, p.getObject().hashCode());
                p.getObject().disconnect();
            }


            @Override
            public boolean validateObject(PooledObject<FTPSession> p) {
                try {
                    log.debug("{}FTP会话验证 - {}", LOG_PREFIX, p.getObject().hashCode());
                    if (p.getObject().printWorkingDirectory() != null) {
                        log.debug("{}FTP会话验证成功 - {}", LOG_PREFIX, p.getObject().hashCode());
                        return true;
                    } else {
                        log.error("{}FTP会话cwd验证失败：{}", LOG_PREFIX, property.getPath());
                        return false;
                    }
                } catch (IOException e) {
                    log.error("{}FTP会话失效：{}", LOG_PREFIX, e);
                    return false;
                }
            }
        }, config -> {
            config.setTestOnReturn(true);
            config.setTestOnBorrow(true);
        });
    }

    public FTPSession getSession() throws IOException {
        try {
            return pool.borrowObject();
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    @Override
    public void close() throws IOException {
        try {
            pool.clear();
        } catch (Exception e) {
            log.error("{}FTP连接池清空失败：{}", LOG_PREFIX, e);
        }
        pool.close();
    }

    @Override
    public boolean isEmptyDirectory(String path) throws IOException {
        try(FTPSession session = getSession()) {
            FTPFile[] files = session.listFiles(path);
            return files == null || files.length == 0;
        }
    }

    @Override
    public Resource getResource(String path) throws IOException {
        FTPFile file;
        try(FTPSession session = getSession()) {
            FTPFile[] ftpFiles = session.listFiles(path);
            if (ftpFiles == null || ftpFiles.length == 0) {
                session.close();
                return null;
            }
            file = ftpFiles[0];
        } catch (ParserInitializationException e) {
            throw new IOException(e.getMessage(), e);
        } catch (Exception e) {
            log.error("{}获取资源失败:{}", LOG_PREFIX, e);
            return null;
        }

        return new FTPPoolResource(this, file, path);
    }

    @Override
    public List<FileInfo> listFiles(String path) throws IOException {
         try(FTPSession session = getSession()) {
            FTPFile[] ftpFiles = session.listFiles(path);
            return Arrays.stream(ftpFiles)
                    .map(this::toFileInfo)
                    .collect(Collectors.toList());
        }
    }

    public FileInfo toFileInfo(FTPFile file) {
        FileInfo fileInfo = new FileInfo();
        fileInfo.setName(file.getName());
        fileInfo.setType(file.isDirectory() ? FileInfo.TYPE_DIR : FileInfo.TYPE_FILE);
        fileInfo.setSize(file.isDirectory() ? -1 : file.getSize());
        fileInfo.setLastModified(file.getTimestamp().getTimeInMillis());
        fileInfo.setCreatedAt(file.getTimestamp().getTime());
        fileInfo.setUpdatedAt(file.getTimestamp().getTime());
        fileInfo.setMount(true);
        return fileInfo;
    }

    @Override
    public FileInfo getFileInfo(String path) throws IOException {
        try(FTPSession session = getSession()) {
            FTPFile[] ftpFiles = session.listFiles(path);

            if (ftpFiles == null || ftpFiles.length == 0) {
                return null;
            } else {
                return toFileInfo(ftpFiles[0]);
            }
        }
    }

    @Override
    public boolean delete(String path) throws IOException {
        throw new UnknownServiceException("FTP目前只支持只读模式");
    }

    @Override
    public boolean mkdir(String path) throws IOException {
        throw new UnknownServiceException("FTP目前只支持只读模式");
    }

    @Override
    public long store(String path, long size, InputStream inputStream) throws IOException {
        throw new UnknownServiceException("FTP目前只支持只读模式");
    }

    @Override
    public OutputStream newOutputStream(String path) throws IOException {
        throw new UnknownServiceException("FTP目前只支持只读模式");
    }

    @Override
    public boolean rename(String path, String newName) throws IOException {
        throw new UnknownServiceException("FTP目前只支持只读模式");
    }

    @Override
    public boolean copy(String src, String dest) throws IOException {
        throw new UnknownServiceException("FTP目前只支持只读模式");
    }

    @Override
    public boolean move(String src, String dest) throws IOException {
        throw new UnknownServiceException("FTP目前只支持只读模式");
    }

    @Override
    public boolean mkdirs(String path) throws IOException {
        throw new UnknownServiceException("FTP目前只支持只读模式");
    }

    @Override
    public boolean exist(String path) throws IOException {
        return getFileInfo(path) != null;
    }


}
