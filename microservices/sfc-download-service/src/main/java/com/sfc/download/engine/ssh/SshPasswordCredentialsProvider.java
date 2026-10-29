package com.sfc.download.engine.ssh;

import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.URIish;

import java.util.Objects;

/**
 * 提供 SSH 用户名 + 密码的 JGit 凭证提供器。
 *
 * <p><b>为什么不能直接用 {@code UsernamePasswordCredentialsProvider}：</b>JGit 的
 * {@code SshdSessionFactory} 会把 {@link CredentialsProvider} 包装成
 * {@code JGitUserInteraction}，而后者只有在 {@link #isInteractive()} 返回 {@code true} 时才会向
 * sshd 提供交互式密码输入（{@code JGitUserInteraction#isInteractionAllowed}）。sshd 的密码认证
 * 要么使用已添加的 password identity（JGit 从不添加），要么通过交互式回调取密码；而
 * {@code UsernamePasswordCredentialsProvider#isInteractive()} 硬编码为 {@code false}，
 * 导致密码永远不会被发送、认证必然失败。</p>
 *
 * <p>本类只支持 {@link CredentialItem.Username} 与 {@link CredentialItem.Password} 两类凭证项，
 * 其余类型一律返回不支持（不猜测、不填充），避免把密码写给其它类型的提示。</p>
 */
public final class SshPasswordCredentialsProvider extends CredentialsProvider {

    /**
     * SSH 用户名。
     */
    private final String username;

    /**
     * SSH 密码。
     */
    private final String password;

    /**
     * 构造凭证提供器。
     *
     * @param username SSH 用户名，不能为空
     * @param password SSH 密码，不能为空
     * @throws NullPointerException 用户名或密码为 {@code null} 时抛出
     */
    public SshPasswordCredentialsProvider(String username, String password) {
        this.username = Objects.requireNonNull(username, "username 不能为 null");
        this.password = Objects.requireNonNull(password, "password 不能为 null");
    }

    /**
     * 声明本提供器支持交互式输入。
     *
     * <p>这是 SSH 密码认证能生效的关键：JGit 的 {@code JGitUserInteraction} 以此决定是否向 sshd
     * 提供密码。</p>
     *
     * @return 恒为 {@code true}
     */
    @Override
    public boolean isInteractive() {
        return true;
    }

    /**
     * 判断是否支持给定的全部凭证项。
     *
     * <p>除用户名/密码外，JGit 的交互式回调还可能放入「隐藏字符串」与「信息提示」项
     * （键盘交互认证、重试提示等），这里一并声明支持，避免在重试路径上因「不支持」直接失败。</p>
     *
     * @param items 凭证项
     * @return 全部为可处理类型时返回 {@code true}
     */
    @Override
    public boolean supports(CredentialItem... items) {
        for (CredentialItem item : items) {
            // CredentialItem.Username 继承自 StringType，因此这里只需判断 Password/StringType/提示信息
            if (!(item instanceof CredentialItem.Password)
                    && !(item instanceof CredentialItem.StringType)
                    && !(item instanceof CredentialItem.InformationalMessage)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 填充用户名与密码凭证项。
     *
     * <p>隐藏字符串项（键盘交互式密码提示）同样填入密码；信息提示项无需赋值，直接跳过；
     * 其余类型返回 {@code false}（表示无法提供），而不是抛异常——抛出会让上层把「密码错误」
     * 之类的普通认证失败包装成难以理解的 {@code UnsupportedCredentialItem}。</p>
     *
     * @param uri   目标地址（本实现不使用，用户名/密码已按任务确定）
     * @param items 待填充的凭证项
     * @return 全部可处理的项都已填充返回 {@code true}
     */
    @Override
    public boolean get(URIish uri, CredentialItem... items) {
        for (CredentialItem item : items) {
            if (item instanceof CredentialItem.Username usernameItem) {
                usernameItem.setValue(username);
            } else if (item instanceof CredentialItem.Password passwordItem) {
                passwordItem.setValue(password.toCharArray());
            } else if (item instanceof CredentialItem.StringType stringItem) {
                if (!stringItem.isValueSecure()) {
                    return false;
                }
                stringItem.setValue(password);
            } else if (!(item instanceof CredentialItem.InformationalMessage)) {
                return false;
            }
        }
        return true;
    }
}
