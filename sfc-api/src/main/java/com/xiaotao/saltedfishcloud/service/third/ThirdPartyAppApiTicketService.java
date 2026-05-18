package com.xiaotao.saltedfishcloud.service.third;

import com.xiaotao.saltedfishcloud.model.po.ThirdPartyAppApiTicket;
import com.xiaotao.saltedfishcloud.service.CrudService;

import java.util.Optional;

/**
 * 第三方OAuth应用 ApiTicket 业务服务。
 */
public interface ThirdPartyAppApiTicketService extends CrudService<ThirdPartyAppApiTicket> {

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
    int revokeByAppIdAndUid(Long appId, Long uid);

    /**
     * 撤销指定应用指定用户指定类型的全部 ApiTicket。
     *
     * @param appId     第三方应用id
     * @param uid       用户id
     * @param permanent 是否永久票据
     * @return 本次被撤销的票据数量
     */
    int revokeByAppIdAndUidAndPermanent(Long appId, Long uid, Boolean permanent);

    /**
     * 根据票据唯一标识撤销 ApiTicket。
     *
     * @param jti JWT载荷中的唯一票据标识
     * @return 当存在对应持久化记录时返回 {@code true}，否则返回 {@code false}
     */
    boolean revokeByJti(Long jti);
}

