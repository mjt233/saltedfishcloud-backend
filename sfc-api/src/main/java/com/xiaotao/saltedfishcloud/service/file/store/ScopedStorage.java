package com.xiaotao.saltedfishcloud.service.file.store;

import com.xiaotao.saltedfishcloud.model.param.FileTimeAttribute;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.model.progress.FileTransferItem;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.io.Resource;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 带作用域限制的 {@link Storage} 包装器。
 * <p>
 * 该实现负责将外部传入的逻辑路径映射到底层存储的真实路径，并统一处理以下能力：
 * </p>
 * <ul>
 *     <li>路径分隔符标准化</li>
 *     <li>路径中 {@code .}、{@code ..} 片段的清理与越界拦截</li>
 *     <li>挂载根路径前缀拼接</li>
 *     <li>挂载根路径有效性校验</li>
 * </ul>
 * <p>
 * 被包装的底层 {@link Storage} 只需要关注“作用域内路径”的真实存储操作，不再需要自行处理挂载根路径拼接与越界安全逻辑。
 * </p>
 */
public class ScopedStorage implements Storage, Closeable {
    /**
     * 被包装的底层存储实现。
     */
    @Getter
    private final Storage delegate;

    /**
     * 底层存储中的挂载根路径。
     */
    @Getter
    private final String basePath;

    /**
     * 创建一个新的作用域存储包装器。
     *
     * @param delegate 底层存储实现
     * @param basePath 存储根路径；为空时视为根目录
     * @throws IOException 挂载根路径不存在或不可访问时抛出
     */
    public ScopedStorage(Storage delegate, String basePath) throws IOException {
        this.delegate = Objects.requireNonNull(delegate, "delegate storage must not be null");
        this.basePath = normalizeBasePath(basePath);
        if (!delegate.exist(this.basePath)) {
            throw new IOException("存储根路径不存在或不可访问: " + this.basePath);
        }
    }

    @Override
    public boolean delete(String path) throws IOException {
        return delegate.delete(resolve(path));
    }

    @Override
    public boolean mkdir(String path) throws IOException {
        return delegate.mkdir(resolve(path));
    }

    @Override
    public long store(FileInfo fileInfo, String path, long size, InputStream inputStream) throws IOException {
        return delegate.store(fileInfo, resolve(path), size, inputStream);
    }

    @Override
    public OutputStream newOutputStream(String path) throws IOException {
        return delegate.newOutputStream(resolve(path));
    }

    @Override
    public boolean rename(String path, String newName) throws IOException {
        return delegate.rename(resolve(path), newName);
    }

    @Override
    public boolean copy(String src, String dest, @Nullable FileTransferItem transferItem) throws IOException {
        return delegate.copy(resolve(src), resolve(dest), transferItem);
    }

    @Override
    public boolean move(String src, String dest, @Nullable FileTransferItem transferItem) throws IOException {
        return delegate.move(resolve(src), resolve(dest), transferItem);
    }

    @Override
    public void updateTime(String path, List<String> names, FileTimeAttribute attribute) throws IOException {
        delegate.updateTime(resolve(path), names, attribute);
    }

    @Override
    public boolean isEmptyDirectory(String path) throws IOException {
        return delegate.isEmptyDirectory(resolve(path));
    }

    @Override
    public Resource getResource(String path) throws IOException {
        return delegate.getResource(resolve(path));
    }

    @Override
    public List<FileInfo> listFiles(String path) throws IOException {
        return delegate.listFiles(resolve(path));
    }

    @Override
    public FileInfo getFileInfo(String path) throws IOException {
        return delegate.getFileInfo(resolve(path));
    }

    @Override
    public void close() throws IOException {
        try {
            delegate.close();
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("关闭存储失败", e);
        }
    }

    /**
     * 将逻辑路径解析为底层存储的真实路径。
     *
     * @param path 逻辑路径
     * @return 底层存储使用的真实路径
     * @throws IOException 路径非法时抛出
     */
    private String resolve(String path) throws IOException {
        String normalizedRequestPath = normalizeRequestPath(path);
        if ("/".equals(normalizedRequestPath)) {
            return basePath;
        }
        if ("/".equals(basePath)) {
            return normalizedRequestPath;
        }
        String relativePath = normalizedRequestPath.substring(1);
        return basePath.endsWith("/") ? basePath + relativePath : basePath + "/" + relativePath;
    }

    /**
     * 标准化逻辑请求路径。
     *
     * @param path 逻辑路径
     * @return 标准化后的绝对路径，以 {@code /} 开头
     * @throws IOException 路径非法或存在越界片段时抛出
     */
    private String normalizeRequestPath(String path) throws IOException {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        return normalizeSegments(path.replace('\\', '/'), true);
    }

    /**
     * 标准化底层存储根路径。
     *
     * @param path 存储根路径配置
     * @return 标准化后的根路径
     * @throws IOException 路径非法或存在越界片段时抛出
     */
    private String normalizeBasePath(String path) throws IOException {
        if (path == null || path.isBlank()) {
            return "/";
        }
        String normalized = normalizeSegments(path.replace('\\', '/'), false);
        if (normalized.length() > 1 && normalized.endsWith("/") && !isWindowsDriveRoot(normalized)) {
            return normalized.replaceAll("/+$", "");
        }
        return normalized;
    }

    /**
     * 对路径片段进行规范化并拦截越界访问。
     *
     * @param path 请求路径
     * @param ensureLeadingSlash 是否强制为普通路径补全前导 {@code /}
     * @return 标准化后的路径
     * @throws IOException 路径非法时抛出
     */
    private String normalizeSegments(String path, boolean ensureLeadingSlash) throws IOException {
        String candidate = path;
        if (ensureLeadingSlash && !candidate.startsWith("/") && !candidate.startsWith("//")) {
            candidate = "/" + candidate;
        }
        String prefix = resolvePrefix(candidate, ensureLeadingSlash);
        String contentPath = candidate.substring(prefix.length());
        List<String> segments = new ArrayList<>();
        for (String segment : contentPath.split("/+")) {
            if (segment.isEmpty() || ".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment)) {
                throw new IOException("非法路径，不允许越界访问: " + path);
            }
            segments.add(segment);
        }
        if (segments.isEmpty()) {
            return prefix;
        }
        String joinedPath = String.join("/", segments);
        if ("/".equals(prefix)) {
            return "/" + joinedPath;
        }
        if (prefix.endsWith("/")) {
            return prefix + joinedPath;
        }
        return prefix + "/" + joinedPath;
    }

    /**
     * 解析路径前缀，用于保留本地盘符、UNC 前缀或普通根路径。
     *
     * @param path 待解析路径
     * @param ensureLeadingSlash 是否为普通逻辑路径补全前导斜杠
     * @return 路径前缀
     */
    private String resolvePrefix(String path, boolean ensureLeadingSlash) {
        if (path.startsWith("//")) {
            return "//";
        }
        if (isWindowsDrivePath(path)) {
            return path.length() >= 3 && path.charAt(2) == '/' ? path.substring(0, 3) : path.substring(0, 2);
        }
        return ensureLeadingSlash || path.startsWith("/") ? "/" : "";
    }

    /**
     * 判断路径是否为 Windows 盘符路径。
     *
     * @param path 待判断路径
     * @return 是否为 Windows 盘符路径
     */
    private boolean isWindowsDrivePath(String path) {
        return path.length() >= 2 && Character.isLetter(path.charAt(0)) && path.charAt(1) == ':';
    }

    /**
     * 判断路径是否为 Windows 盘符根路径。
     *
     * @param path 待判断路径
     * @return 是否为盘符根路径
     */
    private boolean isWindowsDriveRoot(String path) {
        return path.length() == 3 && isWindowsDrivePath(path) && path.charAt(2) == '/';
    }
}



