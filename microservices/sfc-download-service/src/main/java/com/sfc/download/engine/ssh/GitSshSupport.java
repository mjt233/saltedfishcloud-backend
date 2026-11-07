package com.sfc.download.engine.ssh;

import com.sfc.common.exception.SfcJsonException;
import com.sfc.download.model.po.SshCredential;
import com.sfc.download.model.po.SshCredentialAuthType;
import com.sfc.download.model.po.SshHostKey;
import com.sfc.download.service.SshHostKeyService;
import com.sfc.download.util.SshKeyUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.sshd.ProxyData;
import org.eclipse.jgit.transport.sshd.ProxyDataFactory;
import org.eclipse.jgit.transport.sshd.ServerKeyDatabase;
import org.eclipse.jgit.transport.sshd.SshdSessionFactory;
import org.eclipse.jgit.transport.sshd.agent.ConnectorFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.List;

/**
 * Git SSH 克隆的会话工厂装配器。
 *
 * <p>每个 SSH 克隆任务调用一次 {@link #createSessionFactory} 创建一个独立的
 * {@link SshdSessionFactory}（{@code Closeable}，任务结束必须关闭），把本次任务的
 * 用户凭据、代理与主机密钥策略全部收敛到该实例上，<b>不修改 JVM 全局 SSH 工厂</b>，
 * 因此并发任务可以各自使用不同凭据/代理互不干扰。</p>
 *
 * <p><b>隔离宿主机环境：</b>会话工厂的 home/ssh 目录被指向任务级临时目录，避免读取部署机的
 * {@code ~/.ssh}（私钥、known_hosts、config）。SSH 认证只由用户配置的凭据驱动。</p>
 *
 * <p><b>主机密钥（TOFU）：</b>通过 {@link DatabaseServerKeyDatabase} 用本服务数据库中的
 * 信任记录做指纹比对；未知主机首次连接时自动信任并落库，指纹变化时拒绝连接并记录 WARN。
 * 落库失败时<b>拒绝连接</b>（fail-closed），避免「信任未记录却放行」导致 TOFU 形同失效。</p>
 *
 * @see SshCredential
 * @see SshHostKey
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GitSshSupport {

    /**
     * 日志前缀。
     */
    private static final String LOG_PREFIX = "[Git][SSH]";

    /**
     * SSH 主机密钥信任记录服务。
     */
    private final SshHostKeyService sshHostKeyService;

    /**
     * 为一个 SSH 克隆任务创建独立的 JGit SSH 会话工厂。
     *
     * <p>私钥在<b>内存中</b>解析为 {@link KeyPair}（不落盘），解密口令在解析阶段使用；
     * 密码认证方式不加载任何密钥（密码通过 {@link SshPasswordCredentialsProvider} 提供）。</p>
     *
     * @param credential 用户 SSH 凭据，不能为 {@code null}
     * @param proxy      本次克隆使用的代理，允许为 {@code null}（直连）
     * @param trustUid   首次信任主机密钥时记录的用户 uid，允许为 {@code null}
     * @param sshHome    任务级 SSH 工作目录（用于隔离宿主机 {@code ~/.ssh}），不能为 {@code null}
     * @return 已按本次任务配置好的 SSH 会话工厂
     * @throws SfcJsonException 凭据认证方式非法或私钥无法解析时抛出（HTTP 400 语义）
     */
    public SshdSessionFactory createSessionFactory(SshCredential credential, Proxy proxy, Long trustUid, Path sshHome) {
        SshCredentialAuthType authType = credential.getAuthType();
        if (authType == null) {
            throw new SfcJsonException(400, "SSH 凭据认证方式无效");
        }
        List<KeyPair> keys = authType == SshCredentialAuthType.PRIVATE_KEY
                ? SshKeyUtils.loadKeyPairs(credential.getPrivateKey(), credential.getPassphrase())
                : List.of();
        ServerKeyDatabase serverKeyDatabase = new DatabaseServerKeyDatabase(sshHostKeyService, trustUid);
        TaskSshSessionFactory factory = new TaskSshSessionFactory(keys, serverKeyDatabase, proxy);
        File home = sshHome.toFile();
        factory.setHomeDirectory(home);
        factory.setSshDirectory(home);
        return factory;
    }

    /**
     * 把代理转换为 JGit 的代理数据工厂。
     *
     * @param proxy 代理，允许为 {@code null}
     * @return 代理数据工厂；代理为空时返回 {@code null}（直连）
     */
    private static ProxyDataFactory proxyDataFactory(Proxy proxy) {
        return proxy == null ? null : _ -> new ProxyData(proxy);
    }

    /**
     * 单任务 SSH 会话工厂。
     *
     * <p>只覆写「密钥来源」「主机密钥库」「SSH agent」三个扩展点，其余行为（算法、认证顺序、
     * 代理连接等）沿用 JGit 默认实现。</p>
     */
    private static final class TaskSshSessionFactory extends SshdSessionFactory {

        /**
         * 本次任务的私钥列表（密码认证方式下为空列表）。
         */
        private final List<KeyPair> keys;

        /**
         * 基于数据库信任记录的主机密钥库。
         */
        private final ServerKeyDatabase serverKeyDatabase;

        /**
         * 构造单任务会话工厂。
         *
         * @param keys              私钥列表，不能为 {@code null}
         * @param serverKeyDatabase 主机密钥库，不能为 {@code null}
         * @param proxy             代理，允许为 {@code null}
         */
        private TaskSshSessionFactory(List<KeyPair> keys, ServerKeyDatabase serverKeyDatabase, Proxy proxy) {
            super(null, proxyDataFactory(proxy));
            this.keys = keys;
            this.serverKeyDatabase = serverKeyDatabase;
        }

        /**
         * 使用本次任务凭据中的私钥，不读取宿主机 {@code ~/.ssh} 下的默认密钥。
         *
         * @param sshDir SSH 目录（本次实现不读取）
         * @return 私钥列表
         */
        @Override
        protected Iterable<KeyPair> getDefaultKeys(File sshDir) {
            return keys;
        }

        /**
         * 使用基于本服务数据库的主机密钥库。
         *
         * @param homeDir 会话 home 目录（本次实现不使用）
         * @param sshDir  SSH 目录（本次实现不使用）
         * @return 主机密钥库
         */
        @Override
        protected ServerKeyDatabase createServerKeyDatabase(File homeDir, File sshDir) {
            return serverKeyDatabase;
        }

        /**
         * 禁用 SSH agent 认证。
         *
         * <p>若 classpath 中意外出现 {@code org.eclipse.jgit.ssh.apache.agent}，JGit 默认会使用
         * 部署机上的 SSH agent 身份，这会让任务「借用」服务器身份、绕过用户凭据模型。本服务要求
         * SSH 认证只由任务选择的用户凭据驱动，因此显式返回 {@code null}。</p>
         *
         * @return 恒为 {@code null}（不使用 SSH agent）
         */
        @Override
        protected ConnectorFactory getConnectorFactory() {
            return null;
        }
    }

    /**
     * 基于本服务数据库的主机密钥库（TOFU）。
     *
     * <p>{@link #lookup} 刻意返回空列表：sshd 会把「已知密钥」用于快速放行，而本实现的信任模型是
     * 「按指纹精确比对 + 首次信任落库」，统一在 {@link #accept} 中判定更直接，也不会因为返回了
     * 不匹配的密钥而产生歧义。</p>
     */
    private static final class DatabaseServerKeyDatabase implements ServerKeyDatabase {

        /**
         * 信任记录服务。
         */
        private final SshHostKeyService sshHostKeyService;

        /**
         * 首次信任时记录的用户 uid，允许为 {@code null}。
         */
        private final Long trustUid;

        /**
         * 构造主机密钥库。
         *
         * @param sshHostKeyService 信任记录服务
         * @param trustUid          首次信任来源用户 uid，允许为 {@code null}
         */
        private DatabaseServerKeyDatabase(SshHostKeyService sshHostKeyService, Long trustUid) {
            this.sshHostKeyService = sshHostKeyService;
            this.trustUid = trustUid;
        }

        /**
         * 本实现不使用「已知密钥列表」快速放行，统一在 {@link #accept} 中判定。
         *
         * @param connectAddress 主机标识
         * @param remoteAddress  实际远端地址
         * @param config         ssh 配置
         * @return 恒为空列表
         */
        @Override
        public List<PublicKey> lookup(String connectAddress, InetSocketAddress remoteAddress, Configuration config) {
            return List.of();
        }

        /**
         * 校验服务端主机密钥。
         *
         * <p>规则：</p>
         * <ul>
         *   <li>该主机没有任何信任记录 → 首次信任：落库成功才接受；落库失败则拒绝（fail-closed）；</li>
         *   <li>存在指纹一致的记录 → 接受；</li>
         *   <li>存在记录但指纹都不一致 → 拒绝（主机密钥已变更或存在中间人风险）。</li>
         * </ul>
         *
         * @param connectAddress 主机标识（{@code host} 或 {@code host:port}）
         * @param remoteAddress  实际远端地址
         * @param serverKey      服务端返回的主机公钥
         * @param config         ssh 配置
         * @param provider       凭证提供器（本实现不使用）
         * @return 接受返回 {@code true}
         */
        @Override
        public boolean accept(String connectAddress, InetSocketAddress remoteAddress, PublicKey serverKey,
                              Configuration config, CredentialsProvider provider) {
            String fingerprint = SshKeyUtils.fingerprint(serverKey);
            String keyType = SshKeyUtils.keyType(serverKey);
            List<SshHostKey> known = sshHostKeyService.listByConnectAddress(connectAddress);
            if (known.isEmpty()) {
                boolean trusted = sshHostKeyService.trust(connectAddress, keyType,
                        SshKeyUtils.publicKeyLine(serverKey), fingerprint, trustUid);
                if (trusted) {
                    log.info("{}首次信任主机密钥: host={}, keyType={}, fingerprint={}",
                            LOG_PREFIX, connectAddress, keyType, fingerprint);
                } else {
                    // 信任记录写入失败：不放行，避免「每次都是首次信任」使 TOFU 失效
                    log.warn("{}首次信任主机密钥写入失败，拒绝本次连接: host={}, fingerprint={}",
                            LOG_PREFIX, connectAddress, fingerprint);
                }
                return trusted;
            }
            for (SshHostKey record : known) {
                if (fingerprint.equals(record.getFingerprint())) {
                    return true;
                }
            }
            log.warn("{}主机密钥校验失败（指纹不一致）: host={}, 实际指纹={}, 已信任指纹={}",
                    LOG_PREFIX, connectAddress, fingerprint,
                    known.stream().map(SshHostKey::getFingerprint).toList());
            return false;
        }
    }
}
