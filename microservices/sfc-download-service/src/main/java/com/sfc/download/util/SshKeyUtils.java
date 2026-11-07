package com.sfc.download.util;

import com.sfc.common.exception.SfcJsonException;
import com.sfc.common.utils.SfcStringUtils;
import org.apache.sshd.common.NamedResource;
import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.apache.sshd.common.util.security.SecurityUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;

/**
 * SSH 私钥解析工具（基于 Apache MINA sshd，纯 Java，无外部命令依赖）。
 *
 * <p>供两处使用，保证「保存时校验」与「执行时加载」走完全相同的解析路径：</p>
 * <ul>
 *   <li>{@code SshCredentialServiceImpl}：新增 SSH 凭据时校验私钥是否可解析，并计算公钥指纹；</li>
 *   <li>{@code GitSshSupport}：任务执行时在<b>内存中</b>把私钥解析为 {@link KeyPair}，
 *       再交给 JGit 的 SSH 会话工厂，<b>不把私钥落盘</b>。</li>
 * </ul>
 *
 * <p><b>安全约定：</b>本类不输出任何密钥内容；异常信息只保留可读原因（截断后），
 * 避免把私钥片段带进日志或接口响应。</p>
 */
public final class SshKeyUtils {

    /**
     * 解析私钥时使用的资源名（仅用于错误提示，无实际文件含义）。
     */
    private static final String KEY_RESOURCE_NAME = "ssh-private-key";

    /**
     * 错误信息保留的最大长度，避免超长内容撑爆接口响应。
     */
    private static final int MAX_ERROR_MESSAGE_LENGTH = 200;

    /**
     * 工具类禁止实例化。
     */
    private SshKeyUtils() {
        throw new AssertionError("工具类不允许实例化");
    }

    /**
     * 把私钥文本解析为密钥对列表。
     *
     * <p>支持 OpenSSH 与 PEM 两种常见私钥格式；加密私钥必须提供正确的 {@code passphrase}。
     * 解析在内存中完成（{@link ByteArrayInputStream}），不产生临时文件。</p>
     *
     * @param privateKey 私钥内容（OpenSSH / PEM 文本）
     * @param passphrase 私钥口令；私钥未加密时可为 {@code null} 或空白
     * @return 解析出的密钥对列表，至少包含一个元素
     * @throws SfcJsonException 私钥为空、口令错误、格式不受支持或内容损坏时抛出（HTTP 400 语义）
     */
    public static List<KeyPair> loadKeyPairs(String privateKey, String passphrase) {
        if (!SfcStringUtils.hasText(privateKey)) {
            throw new SfcJsonException(400, "SSH 私钥不能为空");
        }
        FilePasswordProvider passwordProvider = SfcStringUtils.hasText(passphrase)
                ? FilePasswordProvider.of(passphrase)
                : FilePasswordProvider.EMPTY;
        try (ByteArrayInputStream in = new ByteArrayInputStream(privateKey.getBytes(StandardCharsets.UTF_8))) {
            Iterable<KeyPair> identities = SecurityUtils.loadKeyPairIdentities(
                    null, NamedResource.ofName(KEY_RESOURCE_NAME), in, passwordProvider);
            if (identities == null) {
                // sshd 对「无法识别的格式」返回 null 而不是抛异常，这里补一个可读提示
                throw new SfcJsonException(400, "SSH 私钥解析失败: 无法识别私钥格式（仅支持 OpenSSH / PEM 私钥）");
            }
            List<KeyPair> keys = new ArrayList<>();
            for (KeyPair keyPair : identities) {
                if (keyPair != null) {
                    keys.add(keyPair);
                }
            }
            if (keys.isEmpty()) {
                throw new SfcJsonException(400, "SSH 私钥解析失败: 未解析到任何密钥");
            }
            return keys;
        } catch (SfcJsonException e) {
            throw e;
        } catch (IOException | GeneralSecurityException | RuntimeException e) {
            String message = describe(e);
            throw new SfcJsonException(400, "SSH 私钥解析失败" + resolveHint(message, passphrase) + ": " + message);
        }
    }

    /**
     * 根据 sshd 的原始错误信息补充可操作的中文提示。
     *
     * @param message    原始错误信息
     * @param passphrase 调用方提供的私钥口令，允许为 {@code null}
     * @return 提示片段（包含前导中文括号）；无需提示时返回空串
     */
    private static String resolveHint(String message, String passphrase) {
        if (message.contains("No password data") && !SfcStringUtils.hasText(passphrase)) {
            return "（该私钥已加密，需要提供私钥口令）";
        }
        if (message.contains("Mismatched private key check")) {
            return "（私钥口令可能不正确）";
        }
        return "";
    }

    /**
     * 计算公钥指纹（SHA256，OpenSSH 风格 {@code SHA256:...}）。
     *
     * @param publicKey 公钥
     * @return 指纹字符串，永不为 {@code null}
     */
    public static String fingerprint(PublicKey publicKey) {
        return KeyUtils.getFingerPrint(publicKey);
    }

    /**
     * 获取公钥的算法类型，例如 {@code ssh-ed25519}、{@code ssh-rsa}。
     *
     * @param publicKey 公钥
     * @return 算法类型字符串，永不为 {@code null}
     */
    public static String keyType(PublicKey publicKey) {
        return KeyUtils.getKeyType(publicKey);
    }

    /**
     * 把公钥格式化为 OpenSSH 单行文本（形如 {@code ssh-ed25519 AAAAC3Nza...}）。
     *
     * @param publicKey 公钥
     * @return 公钥行文本，永不为 {@code null}
     */
    public static String publicKeyLine(PublicKey publicKey) {
        return PublicKeyEntry.toString(publicKey);
    }

    /**
     * 提取异常链最内层的可读消息并截断。
     *
     * @param e 异常，允许为 {@code null}
     * @return 可读消息，永不为 {@code null}
     */
    private static String describe(Throwable e) {
        if (e == null) {
            return "未知错误";
        }
        Throwable current = e;
        String message = e.getMessage();
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
            if (SfcStringUtils.hasText(current.getMessage())) {
                message = current.getMessage();
            }
        }
        if (!SfcStringUtils.hasText(message)) {
            message = e.getClass().getSimpleName();
        }
        if (message.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return message;
        }
        int end = MAX_ERROR_MESSAGE_LENGTH;
        if (Character.isHighSurrogate(message.charAt(end - 1))) {
            end--;
        }
        return message.substring(0, end) + "...";
    }
}
