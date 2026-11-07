package com.sfc.download.model.po;

import com.sfc.cloud.data.model.SfcAuditModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户级 SSH 凭据（Git SSH 克隆使用）。
 *
 * <p><b>归属与可见性：</b>凭据按用户隔离（{@code uid} 为所有者），只有本人可以查看、使用与删除；
 * 创建 Git 下载任务时通过任务参数 {@code sshCredentialId} 选择，服务端在创建期校验归属，
 * 执行期按 id 读取，<b>凭据内容不会写入任务参数</b>（避免随任务记录扩散到 core 的异步任务表）。</p>
 *
 * <p><b>敏感字段：</b>{@link #privateKey}、{@link #passphrase}、{@link #password} 属于机密信息：
 * 接口响应一律不返回（见 {@code SshCredentialVO}），日志不得输出。实体刻意使用
 * {@code @Getter}/{@code @Setter} 而非 {@code @Data}，避免 Lombok 生成包含密钥内容的
 * {@code toString()} 被误打印。</p>
 *
 * <p><b>存储形态：</b>与宿主（core）现有 {@code aria2.secret}、第三方平台凭证的做法保持一致，
 * 凭据以明文存放于本服务独立 SQLite（{@code download.db}）；部署时应限制该数据库文件的访问权限。</p>
 *
 * @see SshCredentialAuthType
 */
@Getter
@Setter
@Entity
@Table(indexes = {
        @Index(name = "idx_ssh_credential_uid", columnList = "uid")
})
public class SshCredential extends SfcAuditModel {

    /**
     * 凭据名称（用户可见，用于在任务创建表单中区分多个凭据）。
     */
    @Column(length = 128)
    private String name;

    /**
     * 认证方式：私钥或密码。
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private SshCredentialAuthType authType;

    /**
     * SSH 用户名（可选）。
     *
     * <p>仓库地址本身通常已携带用户名（如 {@code git@github.com:owner/repo.git}），此时以地址为准；
     * 仅当地址未携带用户名时（如 {@code ssh://host/owner/repo.git}）才会使用本字段补全。
     * 密码认证方式下本字段必填。</p>
     */
    @Column(length = 128)
    private String username;

    /**
     * 私钥内容（OpenSSH / PEM 文本），仅 {@link SshCredentialAuthType#PRIVATE_KEY} 使用。
     */
    @Lob
    private String privateKey;

    /**
     * 私钥口令，仅「带口令的加密私钥」使用。
     */
    @Lob
    private String passphrase;

    /**
     * SSH 密码，仅 {@link SshCredentialAuthType#PASSWORD} 使用。
     */
    @Lob
    private String password;

    /**
     * 私钥对应公钥的指纹（{@code SHA256:...}），保存时自动计算，便于用户在列表中识别密钥。
     *
     * <p>密码认证方式下为空。</p>
     */
    @Column(length = 256)
    private String fingerprint;
}
