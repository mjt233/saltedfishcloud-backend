package com.sfc.download.model.param;

import com.sfc.download.model.po.SshCredential;
import com.sfc.download.model.po.SshCredentialAuthType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 新增 SSH 凭据的请求参数。
 *
 * <p>字段与 {@link SshCredential} 一一对应，但只暴露「新增」所需的部分；{@code id}/{@code uid}
 * 等由服务端决定，不接受客户端传入。认证方式决定哪些字段必填：</p>
 * <ul>
 *   <li>{@link SshCredentialAuthType#PRIVATE_KEY}：{@code privateKey} 必填，{@code passphrase} 可选；</li>
 *   <li>{@link SshCredentialAuthType#PASSWORD}：{@code username} 与 {@code password} 必填。</li>
 * </ul>
 *
 * <p>敏感字段（{@code privateKey}/{@code passphrase}/{@code password}）只进不出：接口响应使用
 * {@link com.sfc.download.model.vo.SshCredentialVO}，不包含这些字段。</p>
 */
public class SshCredentialCreateParam {

    /**
     * 凭据名称（用户可见，用于区分多个凭据）。
     */
    @NotBlank(message = "SSH 凭据名称不能为空")
    @Size(max = 128, message = "SSH 凭据名称不能超过 128 个字符")
    public String name;

    /**
     * 认证方式：私钥或密码。
     */
    @NotNull(message = "SSH 认证方式不能为空")
    public SshCredentialAuthType authType;

    /**
     * SSH 用户名（可选；密码认证方式下必填）。
     */
    @Size(max = 128, message = "SSH 用户名不能超过 128 个字符")
    public String username;

    /**
     * 私钥内容（OpenSSH / PEM 文本），仅私钥认证方式使用。
     */
    @Size(max = 32768, message = "SSH 私钥内容过长")
    public String privateKey;

    /**
     * 私钥口令，仅「带口令的加密私钥」使用。
     */
    @Size(max = 4096, message = "SSH 私钥口令过长")
    public String passphrase;

    /**
     * SSH 密码，仅密码认证方式使用。
     */
    @Size(max = 4096, message = "SSH 密码过长")
    public String password;

    /**
     * 转换为待保存的凭据实体。
     *
     * <p>只搬运请求字段；{@code id}/{@code uid} 由服务层强制覆盖，认证方式相关的字段组合校验
     * 由服务层统一完成（Bean Validation 只负责长度与非空这类格式约束）。</p>
     *
     * @return 凭据实体
     */
    public SshCredential toEntity() {
        SshCredential entity = new SshCredential();
        entity.setName(name);
        entity.setAuthType(authType);
        entity.setUsername(username);
        entity.setPrivateKey(privateKey);
        entity.setPassphrase(passphrase);
        entity.setPassword(password);
        return entity;
    }
}
