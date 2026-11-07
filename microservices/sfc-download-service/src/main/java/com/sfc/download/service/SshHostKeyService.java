package com.sfc.download.service;

import com.sfc.cloud.data.service.SfcCrudService;
import com.sfc.download.model.po.SshHostKey;

import java.util.List;

/**
 * SSH 主机密钥信任记录服务（TOFU，首次信任即记录）。
 *
 * <p>供两处使用：</p>
 * <ul>
 *   <li><b>执行期校验</b>：{@code GitSshSupport} 中的 {@code ServerKeyDatabase} 用
 *       {@link #listByConnectAddress(String)} 取已知记录做指纹比对，未知主机则调用
 *       {@link #trust(String, String, String, String, Long)} 落库；</li>
 *   <li><b>管理员维护</b>：{@link #listAll()} / {@link #listByConnectAddress(String)} 查询、
 *       {@link #deleteRecord(Long)} 删除（主机密钥变更导致克隆失败时使用）。</li>
 * </ul>
 *
 * @see SshHostKey
 */
public interface SshHostKeyService extends SfcCrudService<SshHostKey> {

    /**
     * 查询全部信任记录（按首次信任时间倒序）。
     *
     * @return 信任记录列表，可能为空列表
     */
    List<SshHostKey> listAll();

    /**
     * 查询指定主机标识下的信任记录。
     *
     * @param connectAddress 主机标识（{@code host} 或 {@code host:port}），允许为 {@code null} 或空白
     * @return 该主机的信任记录列表；入参为空白时返回空列表
     */
    List<SshHostKey> listByConnectAddress(String connectAddress);

    /**
     * 记录一次「首次信任」。
     *
     * <p>幂等：同一「主机 + 指纹」已存在时直接返回 {@code true}。调用方必须把返回值当作
     * <b>信任是否成立</b>的依据（fail-closed）：写库失败返回 {@code false}，调用方应拒绝本次 SSH
     * 连接，否则「信任未记录却放行」会让 TOFU 形同失效。</p>
     *
     * @param connectAddress 主机标识
     * @param keyType        公钥算法类型
     * @param publicKey      公钥内容（OpenSSH 单行格式）
     * @param fingerprint    公钥指纹
     * @param uid            首次信任来源用户 uid，允许为 {@code null}
     * @return 信任记录已存在或写入成功返回 {@code true}；写入失败返回 {@code false}
     */
    boolean trust(String connectAddress, String keyType, String publicKey, String fingerprint, Long uid);

    /**
     * 删除一条信任记录。
     *
     * @param id 记录 id
     * @throws com.sfc.common.exception.SfcJsonException id 为空时抛出 400；记录不存在时抛出 404
     */
    void deleteRecord(Long id);
}
