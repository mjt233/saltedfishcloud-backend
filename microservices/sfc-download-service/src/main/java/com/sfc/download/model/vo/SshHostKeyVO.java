package com.sfc.download.model.vo;

import com.sfc.download.model.po.SshHostKey;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * SSH 主机密钥信任记录的对外展示模型（仅管理员可见）。
 *
 * <p>公钥本身不是机密信息，展示出来便于管理员人工核对指纹；{@code uid} 表示首次信任该主机
 * 密钥的用户，用于审计。</p>
 *
 * @see SshHostKey
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SshHostKeyVO {

    /**
     * 记录 id（本服务 Long 统一序列化为字符串）。
     */
    private Long id;

    /**
     * 主机标识：标准端口为 {@code host}，非标准端口为 {@code host:port}。
     */
    private String connectAddress;

    /**
     * 公钥算法类型，例如 {@code ssh-ed25519}。
     */
    private String keyType;

    /**
     * 公钥内容（OpenSSH 单行格式）。
     */
    private String publicKey;

    /**
     * 公钥指纹（{@code SHA256:...}）。
     */
    private String fingerprint;

    /**
     * 首次信任该主机密钥的用户 uid，可能为空。
     */
    private Long uid;

    /**
     * 首次信任时间。
     */
    private Date createAt;

    /**
     * 由信任记录实体构造展示模型。
     *
     * @param entity 信任记录实体，允许为 {@code null}
     * @return 展示模型；入参为 {@code null} 时返回 {@code null}
     */
    public static SshHostKeyVO of(SshHostKey entity) {
        if (entity == null) {
            return null;
        }
        return SshHostKeyVO.builder()
                .id(entity.getId())
                .connectAddress(entity.getConnectAddress())
                .keyType(entity.getKeyType())
                .publicKey(entity.getPublicKey())
                .fingerprint(entity.getFingerprint())
                .uid(entity.getUid())
                .createAt(entity.getCreateAt())
                .build();
    }
}
