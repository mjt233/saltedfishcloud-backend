package com.sfc.ext.webdav.store.handler;

import com.github.sardine.DavResource;
import com.github.sardine.Sardine;
import com.github.sardine.SardineFactory;
import com.github.sardine.impl.SardineException;
import com.sfc.archive.constant.RejectRegex;
import com.sfc.ext.webdav.store.WebDavFileResource;
import com.sfc.ext.webdav.store.model.WebDavClientProperty;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.store.DirectRawStoreHandler;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.regex.Pattern;

public class WebDavStoreRawHandler implements DirectRawStoreHandler {
    private final WebDavClientProperty property;
    private final Sardine sardine;

    public WebDavStoreRawHandler(WebDavClientProperty property) {
        this.property = property;
        this.formatProperty();
        this.validProperty();
        if (property.getIsAnonymous()) {
            this.sardine = SardineFactory.begin();
        } else {
            this.sardine = SardineFactory.begin(property.getUsername(), property.getPassword());
        }
    }

    private void validProperty() {
        if (property.getBasePath() != null && Pattern.compile(RejectRegex.PATH).matcher(property.getBasePath()).find()) {
            throw new IllegalArgumentException("非法的路径，不得包含/../或使用/..结尾");
        }
    }

    private void formatProperty() {
        if (property.getHost().endsWith("/")) {
            // 去掉host末尾的/
            property.setHost(property.getHost().replaceAll("/+$", ""));
        }
    }

    private String getFullPath(String path) {
        return property.getProtocol() + "://" + property.getHost() + encodePath(path);
    }

    private String encodePath(String path) {
        return java.net.URLEncoder.encode(path, StandardCharsets.UTF_8)
                .replaceAll("%2F", "/")
                .replace("+", "%20");
    }

    private static FileInfo convertToFileInfo(DavResource info) {
        Optional<Date> modifyDate = Optional.ofNullable(info.getModified())
                .or(() -> Optional.ofNullable(info.getCreation()));
        Optional<Date> createDate = Optional.ofNullable(info.getCreation())
                .or(() -> modifyDate);

        FileInfo fileInfo = new FileInfo();
        fileInfo.setName(info.getName());
        fileInfo.setMtime(modifyDate.map(Date::getTime).orElse(null));
        fileInfo.setCtime(createDate.map(Date::getTime).orElse(null));
        fileInfo.setUpdateAt(modifyDate.orElse(null));
        fileInfo.setCreateAt(createDate.orElse(null));
        fileInfo.setSize(info.isDirectory() ? -1 : info.getContentLength());
        fileInfo.setMd5(info.getEtag());
        fileInfo.setType(info.isDirectory() ? FileInfo.TYPE_DIR : FileInfo.TYPE_FILE);
        return fileInfo;
    }

    /**
     * 校验是否为只读模式，如果是则抛出异常
     */
    private void checkReadOnly() {
        if (Boolean.TRUE.equals(property.getIsReadOnly())) {
            throw new JsonException(403, "存储处于只读模式，不允许执行写操作");
        }
    }

    @Override
    public boolean isEmptyDirectory(String path) throws IOException {
        return listFiles(path).isEmpty();
    }

    @Override
    public Resource getResource(String path) throws IOException {
        List<DavResource> davResources = sardineList(path, 0);
        if (davResources == null || davResources.isEmpty() || davResources.get(0).isDirectory()) {
            return null;
        }
        return new WebDavFileResource(davResources.get(0), () -> sardine.get(getFullPath(path)));
    }

    @Override
    public List<FileInfo> listFiles(String path) throws IOException {
        List<DavResource> davResources = sardineList(path, 1);
        if (davResources == null || davResources.isEmpty()) {
            return null;
        }
        return davResources.stream()
                // 第一个元素是目录本身，所以跳过
                .skip(1)
                .map(WebDavStoreRawHandler::convertToFileInfo)
                .sorted(Comparator.comparing(FileInfo::getName))
                .toList();
    }

    /**
     * 调用sardine的原始list方法，并处理文件不存在异常，文件不存在直接返回null而不是抛出异常
     * @param path  查找的路径
     * @param depth The depth to look at (use 0 for single resource, 1 for directory listing,
     * 	               -1 for infinite recursion)
     * @return
     * @throws IOException
     */
    private List<DavResource> sardineList(String path, int depth) throws IOException {
        try {
            List<DavResource> list = sardine.list(getFullPath(path), depth);
            if (list == null || list.isEmpty()) {
                return null;
            }
            return list;
        } catch (SardineException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND.value()) {
                return null;
            }
            throw e;
        }
    }

    @Override
    public FileInfo getFileInfo(String path) throws IOException {
        return Optional.ofNullable(sardineList(path, 0))
                .filter(list -> !list.isEmpty())
                .map(list -> convertToFileInfo(list.get(0)))
                .orElse(null);
    }

    @Override
    public boolean delete(String path) throws IOException {
        checkReadOnly();
        sardine.delete(getFullPath(path));
        return true;
    }

    @Override
    public boolean mkdir(String path) throws IOException {
        checkReadOnly();
        sardine.createDirectory(getFullPath(path));
        return true;
    }

    @Override
    public long store(FileInfo fileInfo, String path, long size, InputStream inputStream) throws IOException {
        checkReadOnly();
        String encodedFileName = encodePath(fileInfo.getName());
        sardine.put(
                getFullPath(path) + StringUtils.appendPath("/", encodedFileName),
                inputStream
        );
        return size;
    }

    @Override
    public OutputStream newOutputStream(String path) throws IOException {
        checkReadOnly();
        FileInfo fileInfo = new FileInfo();
        fileInfo.setName(PathUtils.getLastNode(path));
        String parentPath = PathUtils.getParentPath(path);

        PipedInputStream pipedInputStream = new PipedInputStream();
        PipedOutputStream pipedOutputStream = new PipedOutputStream(pipedInputStream);
        Semaphore semaphore = new Semaphore(1);
        try {
            semaphore.acquire(1);
        } catch (InterruptedException ignore) { }
        new Thread(() -> {
            try (pipedInputStream){
                store(fileInfo, parentPath, -1, pipedInputStream);
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                semaphore.release(1);
            }
        }).start();
        return new FilterOutputStream(pipedOutputStream) {
            private volatile boolean closed = false;
            @Override
            public void close() throws IOException {
                if (closed) {
                    return;
                }
                synchronized (this) {
                    if (closed) {
                        return;
                    }
                    super.close();
                    try {
                        semaphore.acquire(1);
                    } catch (InterruptedException ignore) { }
                    closed = true;
                }
            }
        };
    }

    @Override
    public boolean rename(String path, String newName) throws IOException {
        checkReadOnly();
        String sourceFullPath = getFullPath(path);
        String targetFullPath = getFullPath(StringUtils.appendPath(PathUtils.getParentPath(path), encodePath(newName)));
        sardine.move(
                sourceFullPath,
                targetFullPath,
                true
        );
        return true;
    }

    @Override
    public boolean copy(String src, String dest) throws IOException {
        checkReadOnly();
        sardine.copy(getFullPath(src), getFullPath(dest), true);
        return true;
    }

    @Override
    public boolean move(String src, String dest) throws IOException {
        checkReadOnly();
        sardine.move(getFullPath(src), getFullPath(dest), true);
        return true;
    }
}
