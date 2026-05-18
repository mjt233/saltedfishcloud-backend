package com.xiaotao.saltedfishcloud.service;

import com.xiaotao.saltedfishcloud.dao.jpa.ThirdPartyAppApiTicketRepo;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyAppApiTicket;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyAppApiTicketService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 第三方OAuth应用 ApiTicket 业务服务实现。
 */
@Service
public class ThirdPartyAppApiTicketServiceImpl extends CrudServiceImpl<ThirdPartyAppApiTicket, ThirdPartyAppApiTicketRepo> implements ThirdPartyAppApiTicketService {

    /**
     * 根据票据唯一标识查询 ApiTicket 记录。
     *
     * @param jti JWT载荷中的唯一票据标识
     * @return ApiTicket 记录
     */
    @Override
    public Optional<ThirdPartyAppApiTicket> findByJti(Long jti) {
        return repository.findByJti(jti);
    }

    /**
     * 撤销指定应用指定用户的全部 ApiTicket。
     *
     * @param appId 第三方应用id
     * @param uid   用户id
     * @return 本次被撤销的票据数量
     */
    @Override
    @Transactional
    public int revokeByAppIdAndUid(Long appId, Long uid) {
        return repository.revokeByAppIdAndUid(appId, uid);
    }

    /**
     * 撤销指定应用指定用户指定类型的全部 ApiTicket。
     *
     * @param appId     第三方应用id
     * @param uid       用户id
     * @param permanent 是否永久票据
     * @return 本次被撤销的票据数量
     */
    @Override
    @Transactional
    public int revokeByAppIdAndUidAndPermanent(Long appId, Long uid, Boolean permanent) {
        return repository.revokeByAppIdAndUidAndPermanent(appId, uid, permanent);
    }

    /**
     * 根据票据唯一标识撤销 ApiTicket。
     *
     * @param jti JWT载荷中的唯一票据标识
     * @return 当存在对应持久化记录时返回 {@code true}，否则返回 {@code false}
     */
    @Override
    @Transactional
    public boolean revokeByJti(Long jti) {
        Optional<ThirdPartyAppApiTicket> ticketRecord = repository.findByJti(jti);
        if (ticketRecord.isEmpty()) {
            return false;
        }
        ThirdPartyAppApiTicket record = ticketRecord.get();
        if (!Boolean.TRUE.equals(record.getRevoked())) {
            record.setRevoked(true);
            repository.save(record);
        }
        return true;
    }
}

