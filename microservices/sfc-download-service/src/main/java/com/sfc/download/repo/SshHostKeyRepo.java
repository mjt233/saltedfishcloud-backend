package com.sfc.download.repo;

import com.sfc.cloud.data.repo.SfcBaseRepo;
import com.sfc.download.model.po.SshHostKey;

import java.util.List;

/**
 * SSH 主机密钥信任记录仓储。
 *
 * <p>常规增删继承自 {@link SfcBaseRepo}；本接口只补充信任记录特有的查询：按主机标识过滤与
 * 「主机 + 指纹」是否存在（TOFU 写入前的幂等判断）。</p>
 */
public interface SshHostKeyRepo extends SfcBaseRepo<SshHostKey> {

    /**
     * 查询全部信任记录，按 id 倒序（即首次信任时间倒序）。
     *
     * @return 信任记录列表，可能为空列表
     */
    List<SshHostKey> findAllByOrderByIdDesc();

    /**
     * 查询指定主机标识下的全部信任记录，按 id 倒序。
     *
     * @param connectAddress 主机标识（{@code host} 或 {@code host:port}）
     * @return 该主机的信任记录列表，可能为空列表
     */
    List<SshHostKey> findByConnectAddressOrderByIdDesc(String connectAddress);

    /**
     * 判断指定主机与指纹的信任记录是否已存在。
     *
     * @param connectAddress 主机标识（{@code host} 或 {@code host:port}）
     * @param fingerprint    公钥指纹（{@code SHA256:...}）
     * @return 已存在返回 {@code true}
     */
    boolean existsByConnectAddressAndFingerprint(String connectAddress, String fingerprint);
}
