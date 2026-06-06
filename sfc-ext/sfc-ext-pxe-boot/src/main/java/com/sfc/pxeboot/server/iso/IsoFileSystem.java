package com.sfc.pxeboot.server.iso;

import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * ISO 文件系统抽象接口（无状态服务）。
 * 每次方法调用内部自行管理 ISO 文件的打开与关闭。
 * <p>实现类只需实现 {@link #traverse} 和 {@link #getResource} 两个核心方法，
 * 其余方法提供基于 traverse 的默认实现。</p>
 *
 * <h3>路径规范</h3>
 * <p>所有路径参数及 {@link IsoFileEntry#getPath()} 均遵循以下格式：</p>
 * <ol>
 *   <li>以 {@code /} 开头</li>
 *   <li>除根目录 {@code /} 外，不以 {@code /} 结尾</li>
 * </ol>
 * <p>实现类负责将底层库的路径格式转换为上述规范。</p>
 */
public interface IsoFileSystem {

    /**
     * 列出指定目录下的直接子文件/子目录名称。
     *
     * @param dirPath 目录路径（如 "/boot"、"/casper"，根目录为 "/"）
     * @return 直接子项元数据列表，目录不存在时返回空列表
     */
    default List<IsoFileEntry> listFiles(String dirPath) throws IOException {
        List<IsoFileEntry> result = new ArrayList<>();
        traverse(entry -> {
            if (isDirectChild(entry.entry().getPath(), dirPath)) {
                result.add(entry.entry());
            }
            return true;
        });
        return result;
    }

    /**
     * 按正则模式搜索匹配的文件路径。
     *
     * @param pattern  文件名正则表达式
     * @param basePath 限定搜索的目录前缀（如 "/boot"），null 表示搜索整个 ISO
     * @return 匹配的文件条目列表
     */
    default List<IsoFileEntry> findFilesByPattern(String pattern, String basePath) throws IOException {
        Pattern regex = Pattern.compile(pattern.toLowerCase());

        List<IsoFileEntry> result = new ArrayList<>();
        traverse(entry -> {
            String entryPath = entry.entry().getPath();
            if (basePath == null || isUnderPath(entryPath.toLowerCase(), basePath.toLowerCase())) {
                if (regex.matcher(entry.entry().getName().toLowerCase()).find()) {
                    result.add(entry.entry());
                }
            }
            return true;
        });
        return result;
    }

    /**
     * 获取文件资源（惰性加载）。
     * <p>返回的 Resource 在 getInputStream() 调用时才打开 ISO，
     * InputStream 关闭时自动释放底层 ISO 句柄。</p>
     *
     * @param filePath 文件路径（如 "/boot/vmlinuz"）
     * @return 文件资源，未找到时返回 null
     */
    Resource getResource(String filePath) throws IOException;

    /**
     * 判断指定路径的文件或目录是否存在。
     *
     * @param path 文件或目录路径
     * @return 存在返回 true
     */
    default boolean exist(String path) throws IOException {
        boolean[] found = {false};
        traverse(entry -> {
            if (entry.entry().getPath().equalsIgnoreCase(path)
                || entry.entry().getName().equalsIgnoreCase(path)) {
                found[0] = true;
                return false;
            }
            return true;
        });
        return found[0];
    }

    /**
     * 顺序遍历 ISO 内所有文件条目。
     * <p>Predicate 返回 true 继续遍历，返回 false 中止。</p>
     * <p>对于文件条目，可通过 {@link TraversalEntry#openInputStream()} 获取文件内容（目录除外）。</p>
     *
     * @param visitor 条目访问者，返回 false 中止遍历
     */
    void traverse(Predicate<TraversalEntry> visitor) throws IOException;

    /**
     * 可获取 InputStream 的函数式接口。
     */
    @FunctionalInterface
    interface InputStreamSupplier {
        /**
         * 获取输入流。
         *
         * @return 输入流
         * @throws IOException 如果获取失败
         */
        InputStream get() throws IOException;
    }

    /**
     * 遍历条目，包含元数据和可选的文件内容获取能力。
     *
     * @param entry           ISO 文件条目元数据
     * @param openInputStream 文件内容获取器（目录调用时可能抛出异常或返回 null）
     */
    record TraversalEntry(IsoFileEntry entry, InputStreamSupplier openInputStream) {
    }

    // ==================== 内部工具方法 ====================
    // 参数均为已规范化的完整路径（含前导 /，如 "/boot/vmlinuz"）

    /**
     * 判断 entryPath 是否为 targetDir 下的直接下级文件或目录
     */
    private static boolean isDirectChild(String entryPath, String targetDir) {
        if (targetDir.equals("/")) {
            // 根目录：路径中除前导 / 外不含 / 即为直接子项
            return entryPath.indexOf('/') == 0 && entryPath.lastIndexOf('/') == 0;
        }
        String basePrefix = targetDir + "/";
        if (!entryPath.startsWith(basePrefix)) {
            return false;
        }

        // 确保不是比直接子集更深的层级
        return !entryPath.substring(basePrefix.length()).contains("/");
    }

    private static boolean isUnderPath(String entryPath, String prefix) {
        if (prefix.equals("/")) {
            return true;
        }
        return entryPath.startsWith(prefix + "/") || entryPath.equals(prefix);
    }
}
