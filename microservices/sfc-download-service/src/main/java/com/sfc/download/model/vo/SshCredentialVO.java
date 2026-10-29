package com.sfc.download.model.vo;

import com.sfc.download.model.po.SshCredential;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * SSH 凭据的对外展示模型。
 *
 * <p><b>刻意不包含</b>私钥、私钥口令与 SSH 密码：这些字段只进不出，任何接口都不会回显。
 * 前端用 {@code fingerprint} 与 {@code name} 区分多个凭据。</p>
 *
 * @see SshCredential
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SshCredentialVO {

    /**
     * 凭据 id（本服务 Long 统一序列化为字符串）。
     */
    private Long id;

    /**
     * 凭据名称。
     */
    private String name;

    /**
     * 认证方式编码：{@code PRIVATE_KEY} / {@code PASSWORD}。
     */
    private String authType;

    /**
     * SSH 用户名（可能为空；仓库地址自带用户名时以地址为准）。
     */
    private String username;

    /**
     * 私钥对应公钥的指纹（{@code SHA256:...}）；密码认证方式下为空。
     */
    private String fingerprint;

    /**
     * 创建时间。
     */
    private Date createAt;

    /**
     * 由凭据实体构造展示模型。
     *
     * @param entity 凭据实体，允许为 {@code null}
     * @return 展示模型；入参为 {@code null} 时返回 {@code null}
     */
    public static SshCredentialVO of(SshCredential entity) {
        if (entity == null) {
            return null;
        }
        return SshCredentialVO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .authType(entity.getAuthType() == null ? null : entity.getAuthType().name())
                .username(entity.getUsername())
                .fingerprint(entity.getFingerprint())
                .createAt(entity.getCreateAt())
                .build();
    }
}
