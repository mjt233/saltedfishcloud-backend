package com.sfc.download.engine;

import com.sfc.common.exception.SfcJsonException;
import com.sfc.common.utils.SfcStringUtils;
import com.sfc.download.model.DownloadTaskParams;
import com.sfc.download.util.DownloadFileUtils;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 下载来源协议校验。
 *
 * <p>只做一件事：{@link #validateSource(DownloadType, DownloadTaskParams)} 在创建任务阶段按<b>显式指定的</b>
 * 下载类型校验来源协议与格式，把非法地址挡在异步任务之前，并统一给出可读的中文提示。</p>
 *
 * <p><b>类型推断不在这里：</b>「按下载地址推断类型」由各 {@link DownloadEngine#matchesSource(DownloadType, String)}
 * 声明、{@link DownloadEngineRegistry#detect(String)} 汇总；本类不再猜测类型，调用方必须传入具体类型。</p>
 *
 * <p><b>安全约束：</b>Git 允许 {@code http/https}、{@code git://} 与 SSH（{@code ssh://}、
 * {@code git@host:path}）；拒绝 {@code file://}（读取服务端本地文件）、{@code ext::}（git 远程助手
 * 可执行任意命令）、以 {@code -} 开头的参数注入，以及本地绝对路径。</p>
 */
@Component
public class DownloadSourceResolver {

    /** SSH 协议前缀 */
    private static final String SSH_PREFIX = "ssh://";

    /**
     * Windows 盘符形式的绝对路径，例如 {@code C:\repo} 或 {@code C:/repo}。
     */
    private static final Pattern WINDOWS_ABSOLUTE_PATH = Pattern.compile("^[A-Za-z]:[\\\\/].*$");

    /**
     * 创建任务阶段校验下载来源。
     *
     * <p>校验只做格式与协议层面的判断，不发起任何网络请求；类型为空说明调用方漏传必填项，直接拒绝。</p>
     *
     * @param type   下载类型（必填，调用方显式指定）
     * @param params 下载任务参数
     * @throws SfcJsonException 类型为空或来源不合法时抛出（HTTP 400 语义）
     */
    public void validateSource(DownloadType type, DownloadTaskParams params) {
        if (type == null) {
            throw new SfcJsonException(400, "下载类型不能为空");
        }
        String source = params == null ? null : params.url;
        switch (type) {
            case HTTP -> validateHttp(source);
            case GIT -> validateGit(source);
            case MAGNET -> validateMagnet(source);
            case TORRENT -> validateTorrent(params);
            case ED2K -> validateEd2k(source);
            default -> throw new SfcJsonException(400, "不支持的下载类型: " + type.getCode());
        }
    }

    /**
     * 校验 HTTP/HTTPS 下载地址。
     *
     * @param source 下载地址
     * @throws SfcJsonException 不是合法的 HTTP/HTTPS 地址时抛出
     */
    private void validateHttp(String source) {
        if (!DownloadFileUtils.isHttpUrl(source)) {
            throw new SfcJsonException(400, "无效的下载URL: " + describeSource(source));
        }
    }

    /**
     * 校验 Git 仓库地址（协议白名单 + 命令注入防护）。
     *
     * <p>SSH 形式（{@code ssh://} 与 SCP 风格 {@code git@host:path}）同样放行：其传输由
     * JGit 的 ssh.apache 实现承载，认证凭据由任务参数 {@code sshCredentialId} 指定（在创建任务时
     * 另行校验归属），本方法只校验地址语法。</p>
     *
     * @param source 仓库地址
     * @throws SfcJsonException 地址为空、协议不受支持或格式非法时抛出
     */
    private void validateGit(String source) {
        String value = trimToNull(source);
        if (value == null) {
            throw new SfcJsonException(400, "Git 仓库地址不能为空");
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("file:")) {
            throw new SfcJsonException(400, "不支持本地文件协议: " + value);
        }
        if (lower.startsWith("ext::")) {
            throw new SfcJsonException(400, "不支持 ext:: 协议: " + value);
        }
        if (value.startsWith("-")) {
            throw new SfcJsonException(400, "非法的 Git 仓库地址: " + value);
        }
        if (isSshGit(value)) {
            // SSH 形式：要求 host 与 path 均非空（SCP 风格由正则保证 path 非空）
            if (!DownloadFileUtils.isSshGitUrl(value)) {
                throw new SfcJsonException(400, "无效的 SSH Git 仓库地址: " + value);
            }
            return;
        }
        if (isLocalAbsolutePath(value)) {
            throw new SfcJsonException(400, "不支持本地路径: " + value);
        }
        if (!DownloadFileUtils.isValidGitUrl(value)) {
            throw new SfcJsonException(400, "无效的 Git 仓库地址: " + value);
        }
    }

    /**
     * 校验磁力链。
     *
     * @param source 磁力链
     * @throws SfcJsonException 不是合法磁力链时抛出
     */
    private void validateMagnet(String source) {
        if (!DownloadFileUtils.isValidMagnet(source)) {
            throw new SfcJsonException(400, "无效的磁力链: " + describeSource(source));
        }
    }

    /**
     * 校验种子来源：{@code .torrent} 地址或 Base64 种子内容二者其一即可。
     *
     * @param params 下载任务参数
     * @throws SfcJsonException 两者都未提供或地址不是 .torrent 时抛出
     */
    private void validateTorrent(DownloadTaskParams params) {
        if (params == null) {
            throw new SfcJsonException(400, "种子下载参数不能为空");
        }
        if (SfcStringUtils.hasText(params.torrentBase64)) {
            return;
        }
        if (!DownloadFileUtils.isTorrentUrl(params.url)) {
            throw new SfcJsonException(400, "种子下载需要 .torrent 文件地址或种子文件内容（torrentBase64），当前来源: "
                    + describeSource(params.url));
        }
    }

    /**
     * 校验 ed2k 链接。
     *
     * @param source ed2k 链接
     * @throws SfcJsonException 不是合法 ed2k 链接时抛出
     */
    private void validateEd2k(String source) {
        if (!DownloadFileUtils.isValidEd2k(source)) {
            throw new SfcJsonException(400, "无效的 ed2k 链接: " + describeSource(source));
        }
    }

    /**
     * 判断是否为 SSH 形式的 Git 地址。
     *
     * @param value 已去空白的地址
     * @return {@code ssh://} 或 SCP 风格（{@code git@host:path}）返回 {@code true}
     */
    private static boolean isSshGit(String value) {
        return value.toLowerCase(Locale.ROOT).startsWith(SSH_PREFIX)
                || DownloadFileUtils.isScpLikeGit(value);
    }

    /**
     * 判断是否为本地绝对路径（POSIX 风格的 {@code /xxx}、Windows 风格的 {@code C:\xxx} 等）。
     *
     * @param value 已去空白的地址
     * @return 本地绝对路径返回 {@code true}
     */
    private static boolean isLocalAbsolutePath(String value) {
        return value.startsWith("/")
                || value.startsWith("\\")
                || WINDOWS_ABSOLUTE_PATH.matcher(value).matches();
    }

    /**
     * 去空白；空白字符串返回 {@code null}。
     *
     * @param value 原始值，允许为 {@code null}
     * @return 去空白后的值，或 {@code null}
     */
    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 生成用于错误提示的来源摘要（保留原文，仅把 {@code null} 转为可读文案）。
     *
     * @param source 原始来源
     * @return 可读的来源描述
     */
    private static String describeSource(String source) {
        return SfcStringUtils.hasText(source) ? source.trim() : "（空）";
    }
}
