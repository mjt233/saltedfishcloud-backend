package com.sfc.download.repo;

import com.sfc.cloud.data.repo.SfcBaseRepo;
import com.sfc.download.model.po.SshCredential;

import java.util.List;
import java.util.Optional;

/**
 * 用户 SSH 凭据仓储。
 *
 * <p>常规批量操作与按 uid 分页查询继承自 {@link SfcBaseRepo}；本接口只补充「按用户列出全部凭据」
 * 与「按 id + uid 定位」两个查询——后者是创建任务时校验凭据归属、删除凭据时的统一入口。</p>
 */
public interface SshCredentialRepo extends SfcBaseRepo<SshCredential> {

    /**
     * 查询指定用户的全部 SSH 凭据，按 id 倒序（即创建时间倒序）。
     *
     * @param uid 用户 uid
     * @return 该用户的凭据列表，可能为空列表
     */
    List<SshCredential> findAllByUidOrderByIdDesc(Long uid);

    /**
     * 按 id 与所属用户查询凭据。
     *
     * @param id  凭据 id
     * @param uid 用户 uid
     * @return 命中且属于该用户的凭据；不存在或不属于该用户时为空
     */
    Optional<SshCredential> findByIdAndUid(Long id, Long uid);
}
