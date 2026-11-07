package com.sfc.download.model.po;

import com.sfc.cloud.data.model.SfcAuditModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * SSH 主机密钥信任记录（首次信任即 TOFU，Trust On First Use）。
 *
 * <p><b>为什么需要它：</b>SSH 协议依赖主机密钥识别服务端身份。本服务是无人值守的后台服务，
 * 无法像交互式终端那样询问用户是否信任新主机，因此采用「首次连接自动信任并落库」策略：
 * 未知主机首次连接时记录其公钥指纹，之后同一主机再次连接时只接受指纹一致的密钥；
 * 指纹发生变化（主机换钥或中间人攻击）时直接失败，由管理员在信任记录中删除旧记录后再重试。</p>
 *
 * <p><b>作用域：</b>信任记录是<b>服务级全局</b>的（不按用户隔离），因为主机身份属于服务端信任模型，
 * 与具体用户无关。首次信任的触发用户记录在继承字段 {@code uid} 中，便于管理员审计。</p>
 *
 * @see com.sfc.download.engine.ssh.GitSshSupport
 */
@Getter
@Setter
@Entity
@Table(indexes = {
        @Index(name = "idx_ssh_host_key_connect_address", columnList = "connect_address")
}, uniqueConstraints = {
        // 同一主机 + 同一指纹只允许一条记录：并发首次信任时由数据库保证幂等
        @UniqueConstraint(name = "uk_ssh_host_key_address_fingerprint",
                columnNames = {"connect_address", "fingerprint"})
})
public class SshHostKey extends SfcAuditModel {

    /**
     * 主机标识，与 OpenSSH known_hosts 的主机模式一致。
     *
     * <p>标准端口（22）时为 {@code host}，非标准端口时为 {@code host:port}
     * （由 JGit 通过 {@code KnownHostHashValue#createHostPattern} 生成，例如 {@code github.com}
     * 或 {@code git.example.com:2222}）。</p>
     */
    @Column(length = 512)
    private String connectAddress;

    /**
     * 公钥算法类型，例如 {@code ssh-ed25519}、{@code ssh-rsa}。
     */
    @Column(length = 64)
    private String keyType;

    /**
     * 公钥内容（OpenSSH 单行格式，例如 {@code ssh-ed25519 AAAAC3Nza...}）。
     */
    @Lob
    private String publicKey;

    /**
     * 公钥指纹（{@code SHA256:...}），主机密钥校验的比对依据。
     */
    @Column(length = 256)
    private String fingerprint;
}
