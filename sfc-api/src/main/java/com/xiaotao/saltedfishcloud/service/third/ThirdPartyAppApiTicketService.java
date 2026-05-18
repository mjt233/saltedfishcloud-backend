package com.xiaotao.saltedfishcloud.service.third;

import com.xiaotao.saltedfishcloud.model.po.ThirdPartyAppApiTicket;
import com.xiaotao.saltedfishcloud.model.vo.ThirdPartyAppApiTicketPayload;
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

    /**
     * 为指定的应用与用户签发 ApiTicket。该方法负责生成并持久化 ApiTicket 实体。
     *
     * @param payload     签发所需的 JWT 载荷信息
     * @param revokeOlder 若为 {@code true}，在签发前撤销该用户在该应用下已有的旧票据
     * @return 签发后的 ApiTicket 字符串（JWT）
     */
    String issue(ThirdPartyAppApiTicketPayload payload, boolean revokeOlder);

    /**
     * 解析并验证 ApiTicket 字符串的有效性并返回其中的载荷。
     * 若票据无效或已被撤销应抛出运行时异常（由上层处理）。
     *
     * @param apiTicket ApiTicket 字符串（JWT）
     * @return 解析出的 ApiTicket 载荷对象
     */
    ThirdPartyAppApiTicketPayload parseAndValidateApiTicket(String apiTicket);

    /**
     * 查询指定应用与用户的最新一条未被撤销且为永久票据的记录（按创建时间倒序）。
     *
     * @param appId 第三方应用 id
     * @param uid   系统用户 id
     * @return 最新的永久且未撤销的 ApiTicket 持久化记录（如存在）
     */
    Optional<ThirdPartyAppApiTicket> findLatestActivePermanentTicket(Long appId, Long uid);
}


