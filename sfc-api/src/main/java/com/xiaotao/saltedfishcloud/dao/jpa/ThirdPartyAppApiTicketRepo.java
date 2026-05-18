package com.xiaotao.saltedfishcloud.dao.jpa;

import com.xiaotao.saltedfishcloud.dao.BaseRepo;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyAppApiTicket;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 第三方OAuth应用 ApiTicket 持久化仓库。
 */
public interface ThirdPartyAppApiTicketRepo extends BaseRepo<ThirdPartyAppApiTicket> {

    /**
     * 根据票据唯一标识查询 ApiTicket 记录。
     *
     * @param jti JWT载荷中的唯一票据标识
     * @return ApiTicket 记录
     */
    Optional<ThirdPartyAppApiTicket> findByJti(Long jti);

    /**
     * 撤销指定应用指定用户的全部 ApiTicket。
     *
     * @param appId 第三方应用id
     * @param uid   用户id
     * @return 本次被撤销的票据数量
     */
    @Transactional
    @Modifying
    @Query("UPDATE ThirdPartyAppApiTicket t SET t.revoked = true WHERE t.appId = :appId AND t.uid = :uid AND t.revoked = false")
    int revokeByAppIdAndUid(@Param("appId") Long appId, @Param("uid") Long uid);

    /**
     * 撤销指定应用指定用户指定类型的全部 ApiTicket。
     *
     * @param appId     第三方应用id
     * @param uid       用户id
     * @param permanent 是否永久票据
     * @return 本次被撤销的票据数量
     */
    @Transactional
    @Modifying
    @Query("UPDATE ThirdPartyAppApiTicket t SET t.revoked = true WHERE t.appId = :appId AND t.uid = :uid AND t.permanent = :permanent AND t.revoked = false")
    int revokeByAppIdAndUidAndPermanent(@Param("appId") Long appId,
                                        @Param("uid") Long uid,
                                        @Param("permanent") Boolean permanent);

    /**
     * 查询指定应用与用户的最新一条未被撤销且为永久票据的记录（按创建时间倒序）。
     *
     * @param appId 第三方应用 id
     * @param uid   系统用户 id
     * @return 最新的永久且未撤销的 ApiTicket 持久化记录（如存在）
     */
    Optional<ThirdPartyAppApiTicket> findFirstByAppIdAndUidAndPermanentTrueAndRevokedFalseOrderByCreateAtDesc(Long appId, Long uid);
}


