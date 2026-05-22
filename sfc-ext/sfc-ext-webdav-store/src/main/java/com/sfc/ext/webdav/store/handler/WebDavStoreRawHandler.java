package com.sfc.ext.webdav.store.handler;

import com.github.sardine.DavResource;
import com.github.sardine.Sardine;
import com.github.sardine.SardineFactory;
import com.github.sardine.impl.SardineException;
import com.sfc.ext.webdav.store.WebDavFileResource;
import com.sfc.ext.webdav.store.model.WebDavClientProperty;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.model.param.FileTimeAttribute;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.model.progress.FileTransferItem;
import com.xiaotao.saltedfishcloud.service.file.store.Storage;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import com.xiaotao.saltedfishcloud.validator.RejectRegex;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;

import javax.xml.namespace.QName;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.regex.Pattern;

@Slf4j
public class WebDavStoreRawHandler implements Storage, Closeable {
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
        // 启用预认证，在第一次请求时就发送认证信息
        // 这可以避免服务器返回401，减少请求往返
        this.sardine.enablePreemptiveAuthentication(property.getHost());
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
     *
     * @param path  查找的路径
     * @param depth The depth to look at (use 0 for single resource, 1 for directory listing,
     *              -1 for infinite recursion)
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
        String fullPath = getFullPath(path);
        if(sardine.exists(fullPath)) {
            return false;
        } else {
            sardine.createDirectory(fullPath);
        }
        return true;
    }

    @Override
    public long store(FileInfo fileInfo, String path, long size, InputStream inputStream) throws IOException {
        checkReadOnly();
        String encodedFileName = encodePath(fileInfo.getName());
        String mTime = Optional.ofNullable(fileInfo.getMtime())
                .or(() -> Optional.ofNullable(fileInfo.getCtime()))
                .or(() -> Optional.ofNullable(fileInfo.getUpdateAt()).map(Date::getTime))
                .or(() -> Optional.ofNullable(fileInfo.getCreateAt()).map(Date::getTime))
                .map(t -> String.valueOf(t / 1000))
                .orElseGet(() -> String.valueOf(System.currentTimeMillis() / 1000));

        sardine.put(
                getFullPath(path) + StringUtils.appendPath("/", encodedFileName),
                inputStream,
                // 兼容 NextCloud 的文件修改日期header
                Map.of("X-OC-MTime", mTime)
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
        } catch (InterruptedException ignore) {
        }
        new Thread(() -> {
            try (pipedInputStream) {
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
                    } catch (InterruptedException ignore) {
                    }
                    closed = true;
                }
            }
        };
    }

    @Override
    public boolean rename(String path, String newName) throws IOException {
        checkReadOnly();
        String sourceFullPath = getFullPath(path);
        String targetFullPath = getFullPath(StringUtils.appendPath(PathUtils.getParentPath(path), newName));
        sardine.move(
                sourceFullPath,
                targetFullPath,
                true
        );
        return true;
    }

    @Override
    public boolean copy(String src, String dest, @Nullable FileTransferItem item) throws IOException {
        checkReadOnly();
        sardine.copy(getFullPath(src), getFullPath(dest), true);
        return true;
    }

    @Override
    public boolean move(String src, String dest, @Nullable FileTransferItem item) throws IOException {
        checkReadOnly();
        sardine.move(getFullPath(src), getFullPath(dest), true);
        return true;
    }

    @Override
    public void updateTime(String path, List<String> names, FileTimeAttribute attribute) throws IOException {
        checkReadOnly();

        if (names == null || names.isEmpty() || attribute == null) {
            return;
        }

        // 预转换时间，避免在循环中重复格式化
        String mTimeStr = attribute.getModifyTime() != null ? formatToWin32Time(attribute.getModifyTime()) : null;
        String cTimeStr = attribute.getCreateTime() != null ? formatToWin32Time(attribute.getCreateTime()) : null;

        // WebDAV协议中，通常使用PROPPATCH方法来设置文件属性
        // Sardine库提供了patch方法来实现这一点
        // 模拟 Windows WebDAV 客户端行为
        for (String name : names) {
            String fullPath = StringUtils.appendPath(path, name);
            String encodedPath = getFullPath(fullPath);
            // 构建要设置的属性
            Map<QName, String> patchData = new HashMap<>();

            if (attribute.getModifyTime() != null) {
                patchData.put(new QName("urn:schemas-microsoft-com:", "Win32LastModifiedTime"), mTimeStr);
            }

            if (attribute.getCreateTime() != null) {
                patchData.put(new QName("urn:schemas-microsoft-com:", "Win32CreationTime"), cTimeStr);
            }

            // 执行属性更新
            if (!patchData.isEmpty()) {
                sardine.patch(encodedPath, patchData);
            }
        }
    }

    public String formatToWin32Time(Date date) {
        if (date == null) return null;

        // 1. 将旧版 Date 转换为现代的 OffsetDateTime，并锁定为 GMT/UTC 时区
        OffsetDateTime gmtTime = date.toInstant()
                .atZone(ZoneId.of("GMT"))
                .toOffsetDateTime();

        // 2. 使用内置的 RFC_1123_DATE_TIME 格式化器
        // 注意：必须指定 Locale.US，否则星期和月份会被格式化为中文（如“周一”）导致服务器无法识别
        return gmtTime.format(DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.US));
    }

    @Override
    public void close() throws IOException {
        this.sardine.shutdown();
    }
}
