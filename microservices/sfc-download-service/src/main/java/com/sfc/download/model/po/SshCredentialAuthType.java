package com.sfc.download.model.po;

/**
 * SSH 凭据的认证方式。
 *
 * <p>用于区分用户配置的 SSH 凭据是「私钥」还是「密码」：两种方式在 JGit 侧的注入点不同
 * （私钥通过 {@code SshdSessionFactory#getDefaultKeys}，密码通过 {@code CredentialsProvider}），
 * 因此需要在凭据上显式区分，而不是靠字段是否为空来推断。</p>
 *
 * @see SshCredential
 */
public enum SshCredentialAuthType {

    /**
     * 公钥认证：使用用户配置的私钥（可带口令）登录。
     */
    PRIVATE_KEY("私钥"),

    /**
     * 密码认证：使用用户名 + 密码登录。
     */
    PASSWORD("密码");

    /**
     * 面向用户的展示名，用于任务日志与接口文案。
     */
    private final String displayName;

    /**
     * 构造认证方式。
     *
     * @param displayName 面向用户的展示名
     */
    SshCredentialAuthType(String displayName) {
        this.displayName = displayName;
    }

    /**
     * 获取面向用户的展示名。
     *
     * @return 展示名，永不为 {@code null}
     */
    public String getDisplayName() {
        return displayName;
    }
}
