package com.sfc.download.stash.impl;

import com.sfc.cloud.model.SfcFileInfo;
import com.sfc.cloud.remote.client.SfcCoreFileClient;
import com.sfc.common.exception.SfcJsonException;
import com.sfc.common.model.json.SfcJsonResult;
import com.sfc.common.utils.SfcStringUtils;
import com.sfc.download.constant.DownloadTaskConstants;
import com.sfc.download.engine.DownloadArtifact;
import com.sfc.download.stash.DownloadSaveService;
import com.sfc.download.util.DownloadFileUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 下载产物落盘服务实现。
 *
 * <p><b>单文件判定：</b>产物只有一个且 {@code relativePath} 不含层级分隔符时走单文件通道，直接以文件名落盘；
 * 其余情况（多文件、或产物位于子目录中）统一按目录树落盘，从而完整保留引擎产出的结构。</p>
 *
 * <p><b>顶层目录名推断：</b>优先使用调用方给出的 {@link StashRequest#topLevelName()}；未给出时，
 * 若全部产物共享同一个首层目录，则用该目录名；仍无法推断时退化为时间戳。推断结果同样按
 * {@code .1}/{@code .2} 规则做重名规避，避免覆盖用户已有目录。</p>
 *
 * <p><b>路径拼接：</b>网盘路径统一按 {@code "/"} 规范化，{@code "/"}、{@code "/a/b"}、{@code "a/b/"}
 * 等写法都能得到正确结果，不会出现 {@code "//"} 或末尾多余的 {@code "/"}。</p>
 *
 * <p><b>失败语义：</b>目录创建或上传失败立即抛出业务异常；已上传成功的文件不回滚（见接口注释）。</p>
 *
 * @see DownloadSaveService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DownloadSaveServiceImpl implements DownloadSaveService {

    /**
     * 日志前缀，与下载任务其它组件保持一致，便于按前缀检索。
     */
    private static final String LOG_PREFIX = DownloadTaskConstants.LOG_PREFIX;

    /**
     * 网盘根路径。
     */
    private static final String ROOT_PATH = "/";

    /**
     * 网盘路径分隔符（与本地文件系统分隔符无关，统一使用正斜杠）。
     */
    private static final String PATH_SEPARATOR = "/";

    /**
     * core 网盘文件客户端：列目录、创建目录、上传文件。
     */
    private final SfcCoreFileClient fileClient;

    /**
     * {@inheritDoc}
     *
     * <p>先过滤掉目录型产物（目录由文件路径隐式创建），再按「单文件 / 目录树」分流。</p>
     */
    @Override
    public StashResult stash(StashRequest request) {
        if (request == null) {
            throw new SfcJsonException(400, "落盘请求不能为空");
        }
        List<DownloadArtifact> files = requireFiles(request.artifacts());
        String savePath = normalizeSavePath(request.savePath());
        log.info("{}开始落盘下载产物: uid={}, savePath={}, 产物数={}, 顶层目录名={}",
                LOG_PREFIX, request.uid(), savePath, files.size(), request.topLevelName());
        if (isSingleFile(files)) {
            return saveSingleFile(request.uid(), savePath, files.getFirst());
        }
        return saveTree(request.uid(), savePath, request.topLevelName(), files);
    }

    // ==================== 单文件 ====================

    /**
     * 以单个文件的形式落盘：在保存目录下按 {@code .1}/{@code .2} 规则规避重名后上传。
     *
     * @param uid      资源归属用户 uid
     * @param savePath 已规范化的保存目录
     * @param artifact 唯一的文件产物
     * @return 落盘结果（最终文件名与文件大小）
     */
    private StashResult saveSingleFile(long uid, String savePath, DownloadArtifact artifact) {
        requireRegularFile(artifact.localPath());
        String originName = artifact.relativePath();
        String finalName = DownloadFileUtils.generateNonConflictName(originName, listExistingNames(uid, savePath));
        if (!finalName.equals(originName)) {
            log.info("{}目标目录存在同名文件，保存文件名调整: downloadId 产物={}, 调整后={}",
                    LOG_PREFIX, originName, finalName);
        }
        log.info("{}开始上传单文件产物: uid={}, path={}, name={}, size={}字节",
                LOG_PREFIX, uid, savePath, finalName, artifact.size());
        fileClient.upload(new FileSystemResource(artifact.localPath()), uid, savePath, finalName, null).requireSuccess();
        log.info("{}单文件产物落盘完成: uid={}, path={}, name={}, size={}字节",
                LOG_PREFIX, uid, savePath, finalName, artifact.size());
        return new StashResult(finalName, artifact.size());
    }

    // ==================== 目录树 ====================

    /**
     * 以目录树的形式落盘：建顶层目录 → 按相对路径逐级建中间目录 → 逐文件上传。
     *
     * @param uid          资源归属用户 uid
     * @param savePath     已规范化的保存目录
     * @param topLevelName 调用方期望的顶层目录名，可为 {@code null}
     * @param files        已过滤掉目录的产物列表
     * @return 落盘结果（最终顶层目录名与上传字节合计）
     */
    private StashResult saveTree(long uid, String savePath, String topLevelName, List<DownloadArtifact> files) {
        TreePlan plan = buildPlan(topLevelName, files);
        String topDir = DownloadFileUtils.generateNonConflictName(plan.topLevelName(), listExistingNames(uid, savePath));
        if (!topDir.equals(plan.topLevelName())) {
            log.info("{}目标目录存在同名目录，顶层目录名调整: {} -> {}", LOG_PREFIX, plan.topLevelName(), topDir);
        }
        String root = joinPath(savePath, topDir);
        log.info("{}创建顶层目录: uid={}, path={}", LOG_PREFIX, uid, root);
        fileClient.mkdirs(uid, savePath, topDir).requireSuccess();

        // 顶层目录已创建，登记到已创建集合，避免后续重复调用 core 的 mkdirs
        Set<String> createdDirs = new HashSet<>();
        createdDirs.add(root);

        long totalBytes = 0L;
        int uploaded = 0;
        for (TreeEntry entry : plan.entries()) {
            requireRegularFile(entry.localPath());
            int lastSeparator = entry.relativePath().lastIndexOf(PATH_SEPARATOR);
            String dirPart = lastSeparator < 0 ? "" : entry.relativePath().substring(0, lastSeparator);
            String fileName = lastSeparator < 0
                    ? entry.relativePath()
                    : entry.relativePath().substring(lastSeparator + 1);
            String parent = dirPart.isEmpty() ? root : ensureDirectories(uid, root, dirPart, createdDirs);
            String uploadName = DownloadFileUtils.sanitizeFileName(fileName);
            fileClient.upload(new FileSystemResource(entry.localPath()), uid, parent, uploadName, null).requireSuccess();
            totalBytes += entry.size();
            uploaded++;
        }
        log.info("{}目录树落盘完成: uid={}, topDir={}, 文件数={}, 总大小={}字节",
                LOG_PREFIX, uid, topDir, uploaded, totalBytes);
        return new StashResult(topDir, totalBytes);
    }

    /**
     * 构建目录树落盘计划：剥离产物的公共首层目录，并推断顶层目录名。
     *
     * <p>剥离规则：当全部产物的 {@code relativePath} 都具有至少两层、且首层目录名完全一致时，
     * 认为该首层目录就是「顶层目录」，其后的部分才是目录内的相对路径。这样既兼容引擎直接返回
     * {@code <repo>/a/b.txt} 的写法，也兼容返回 {@code a/b.txt}（顶层由 {@code topLevelName} 指定）的写法。</p>
     *
     * @param topLevelName 调用方期望的顶层目录名，可为 {@code null}
     * @param files        产物列表
     * @return 落盘计划
     */
    private static TreePlan buildPlan(String topLevelName, List<DownloadArtifact> files) {
        String commonRoot = commonRoot(files);
        List<TreeEntry> entries = new ArrayList<>(files.size());
        for (DownloadArtifact artifact : files) {
            String relativePath = artifact.relativePath();
            if (commonRoot != null) {
                relativePath = relativePath.substring(commonRoot.length() + 1);
            }
            entries.add(new TreeEntry(relativePath, artifact.localPath(), artifact.size()));
        }
        String name = SfcStringUtils.hasText(topLevelName)
                ? topLevelName
                : (commonRoot == null ? DownloadFileUtils.getTimestampFileName() : commonRoot);
        log.debug("{}目录树落盘计划: 公共首层目录={}, 顶层目录名={}, 条目数={}",
                LOG_PREFIX, commonRoot, name, entries.size());
        return new TreePlan(DownloadFileUtils.sanitizeFileName(name), entries);
    }

    /**
     * 计算全部产物共同的「首层目录」名称。
     *
     * @param files 产物列表
     * @return 公共首层目录名；不存在（有产物直接位于根部，或首层目录名不一致）时返回 {@code null}
     */
    private static String commonRoot(List<DownloadArtifact> files) {
        String root = null;
        for (DownloadArtifact artifact : files) {
            String relativePath = artifact.relativePath();
            int separator = relativePath.indexOf(PATH_SEPARATOR);
            if (separator <= 0) {
                // 产物直接位于根部，或不含层级：没有可剥离的公共首层目录
                return null;
            }
            String first = relativePath.substring(0, separator);
            if (root == null) {
                root = first;
            } else if (!root.equals(first)) {
                return null;
            }
        }
        return root;
    }

    /**
     * 逐级创建中间目录，已创建过的目录不会重复调用 core。
     *
     * @param uid         资源归属用户 uid
     * @param root        顶层目录的完整网盘路径（已创建）
     * @param relativeDir 相对顶层目录的目录路径，使用 {@code "/"} 分隔
     * @param createdDirs 已创建的目录路径集合（会被就地更新）
     * @return 最末一级目录的完整网盘路径
     */
    private String ensureDirectories(long uid, String root, String relativeDir, Set<String> createdDirs) {
        String parent = root;
        for (String segment : relativeDir.split(PATH_SEPARATOR)) {
            if (!SfcStringUtils.hasText(segment)) {
                continue;
            }
            String dir = joinPath(parent, segment);
            if (createdDirs.add(dir)) {
                fileClient.mkdirs(uid, parent, segment).requireSuccess();
            }
            parent = dir;
        }
        return parent;
    }

    // ==================== 通用辅助 ====================

    /**
     * 过滤出真正的文件产物。
     *
     * <p>目录型产物不参与上传（其层级由文件路径隐式创建）；产物为空时按参数错误处理，
     * 避免出现「任务成功但网盘里什么都没有」。</p>
     *
     * @param artifacts 引擎返回的产物列表，允许为 {@code null}
     * @return 非空的文件产物列表
     * @throws SfcJsonException 过滤后没有文件产物时抛出（HTTP 400 语义）
     */
    private static List<DownloadArtifact> requireFiles(List<DownloadArtifact> artifacts) {
        if (artifacts == null || artifacts.isEmpty()) {
            throw new SfcJsonException(400, "下载产物为空");
        }
        List<DownloadArtifact> files = new ArrayList<>(artifacts.size());
        for (DownloadArtifact artifact : artifacts) {
            if (artifact == null) {
                continue;
            }
            if (Files.isDirectory(artifact.localPath())) {
                log.debug("{}跳过目录型产物: {}", LOG_PREFIX, artifact.relativePath());
                continue;
            }
            files.add(artifact);
        }
        if (files.isEmpty()) {
            throw new SfcJsonException(400, "下载产物为空");
        }
        return files;
    }

    /**
     * 判断是否为「单文件」落盘场景。
     *
     * @param files 文件产物列表，不能为空
     * @return 仅有一个产物且不带层级时返回 {@code true}
     */
    private static boolean isSingleFile(List<DownloadArtifact> files) {
        return files.size() == 1 && !files.getFirst().relativePath().contains(PATH_SEPARATOR);
    }

    /**
     * 校验本地产物存在且为普通文件。
     *
     * @param path 本地产物路径
     * @throws SfcJsonException 产物不存在或不是普通文件时抛出（HTTP 500 语义，属引擎实现缺陷）
     */
    private static void requireRegularFile(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            throw new SfcJsonException(500, "下载产物不存在或不是普通文件: " + path);
        }
    }

    /**
     * 列出目标目录下已有的文件/目录名，用于重名规避。
     *
     * @param uid      资源归属用户 uid
     * @param savePath 已规范化的保存目录
     * @return 已有名称列表；目录为空或 core 返回空数据时返回空列表
     * @throws SfcJsonException 无法列出目标目录时抛出
     */
    private List<String> listExistingNames(long uid, String savePath) {
        SfcJsonResult<List<SfcFileInfo>> result = fileClient.list(uid, savePath);
        if (result == null) {
            log.warn("{}列出目标目录失败（响应为空），按目录为空处理: uid={}, path={}", LOG_PREFIX, uid, savePath);
            return List.of();
        }
        result.requireSuccess();
        List<SfcFileInfo> fileInfos = result.getData();
        if (fileInfos == null || fileInfos.isEmpty()) {
            return List.of();
        }
        return fileInfos.stream()
                .map(SfcFileInfo::getName)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 规范化网盘目录路径：反斜杠转正斜杠、折叠重复分隔符、补全开头的 {@code "/"}、去掉末尾多余的 {@code "/"}。
     *
     * @param savePath 原始保存目录，允许为 {@code null} 或空白
     * @return 规范化后的路径，永不为 {@code null}；入参为空时返回 {@code "/"}
     */
    private static String normalizeSavePath(String savePath) {
        if (!SfcStringUtils.hasText(savePath)) {
            return ROOT_PATH;
        }
        String normalized = savePath.trim().replace('\\', '/').replaceAll("/{2,}", PATH_SEPARATOR);
        if (!normalized.startsWith(ROOT_PATH)) {
            normalized = ROOT_PATH + normalized;
        }
        while (normalized.length() > 1 && normalized.endsWith(PATH_SEPARATOR)) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    /**
     * 拼接网盘路径，自动处理 {@code "/"} 的重复与缺失问题。
     *
     * @param base  基础路径，允许为空（按根目录处理）
     * @param parts 追加的路径片段，空白片段会被忽略
     * @return 拼接后的路径，永不为 {@code null} 且永不以 {@code "/"} 结尾（根目录除外）
     */
    private static String joinPath(String base, String... parts) {
        StringBuilder builder = new StringBuilder(normalizeSavePath(base));
        for (String part : parts) {
            if (!SfcStringUtils.hasText(part)) {
                continue;
            }
            String cleaned = part.trim().replace('\\', '/');
            while (cleaned.startsWith(PATH_SEPARATOR)) {
                cleaned = cleaned.substring(1);
            }
            while (cleaned.endsWith(PATH_SEPARATOR)) {
                cleaned = cleaned.substring(0, cleaned.length() - 1);
            }
            if (cleaned.isEmpty()) {
                continue;
            }
            if (!builder.toString().endsWith(PATH_SEPARATOR)) {
                builder.append(PATH_SEPARATOR);
            }
            builder.append(cleaned);
        }
        return builder.isEmpty() ? ROOT_PATH : builder.toString();
    }

    /**
     * 目录树落盘计划。
     *
     * @param topLevelName 期望的顶层目录名（已清洗，尚未做重名规避）
     * @param entries      目录内的条目（路径已剥离公共首层目录）
     */
    private record TreePlan(String topLevelName, List<TreeEntry> entries) {
    }

    /**
     * 目录树中的单个文件条目。
     *
     * @param relativePath 相对顶层目录的路径，使用 {@code "/"} 分隔
     * @param localPath    本地文件路径
     * @param size         文件字节数
     */
    private record TreeEntry(String relativePath, Path localPath, long size) {
    }
}
