package com.xiaotao.saltedfishcloud.service;

import com.xiaotao.saltedfishcloud.constant.error.OAuthError;
import com.xiaotao.saltedfishcloud.dao.jpa.ThirdPartyAppApiTicketRepo;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyAppApiTicket;
import com.xiaotao.saltedfishcloud.model.vo.ThirdPartyAppApiTicketPayload;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyAppApiTicketService;
import com.xiaotao.saltedfishcloud.utils.JwtUtils;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import com.xiaotao.saltedfishcloud.utils.identifier.IdUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

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

    @Override
    @Transactional
    public String issue(ThirdPartyAppApiTicketPayload payload, boolean revokeOlder) {
        ThirdPartyAppApiTicketPayload issuePayload = Optional.ofNullable(payload)
                .orElseThrow(() -> new JsonException(OAuthError.INVALID_TOKEN));
        this.validateIssuePayload(issuePayload);
        if (issuePayload.getJti() == null) {
            issuePayload.setJti(IdUtil.getId());
        }

        boolean permanent = Boolean.TRUE.equals(issuePayload.getPermanent());
        if (revokeOlder) {
            repository.revokeByAppIdAndUidAndPermanent(issuePayload.getAppId(), issuePayload.getUid(), permanent);
        }

        Date now = new Date();
        String apiTicket = JwtUtils.generateToken(MapperHolder.toJsonNoEx(issuePayload),
                permanent ? 0 : (int) TimeUnit.MINUTES.toSeconds(15));
        ThirdPartyAppApiTicket ticketRecord = new ThirdPartyAppApiTicket();
        ticketRecord.setAppId(issuePayload.getAppId());
        ticketRecord.setUid(issuePayload.getUid());
        ticketRecord.setJti(issuePayload.getJti());
        ticketRecord.setPermanent(permanent);
        ticketRecord.setExpiredDate(this.calculateExpiredDate(now, permanent));
        ticketRecord.setApiTicket(apiTicket);
        ticketRecord.setRevoked(false);
        save(ticketRecord);
        return apiTicket;
    }

    @Override
    public ThirdPartyAppApiTicketPayload parseAndValidateApiTicket(String apiTicket) {
        if (!StringUtils.hasText(apiTicket)) {
            throw new JsonException(OAuthError.INVALID_TOKEN);
        }
        try {
            ThirdPartyAppApiTicketPayload payload = MapperHolder.parseJson(JwtUtils.parse(apiTicket), ThirdPartyAppApiTicketPayload.class);
            this.validatePersistedTicket(apiTicket, payload);
            return payload;
        } catch (JsonException | IOException e) {
            throw new JsonException(OAuthError.INVALID_TOKEN);
        }
    }

    @Override
    public Optional<ThirdPartyAppApiTicket> findLatestActivePermanentTicket(Long appId, Long uid) {
        return repository.findFirstByAppIdAndUidAndPermanentTrueAndRevokedFalseOrderByCreateAtDesc(appId, uid)
                .filter(ticket -> ticket.getExpiredDate() == null || new Date().before(ticket.getExpiredDate()))
                .filter(ticket -> StringUtils.hasText(ticket.getApiTicket()));
    }

    /**
     * 校验 ApiTicket 签发载荷是否包含必要字段。
     *
     * @param payload 待签发的 ApiTicket 载荷
     */
    private void validateIssuePayload(ThirdPartyAppApiTicketPayload payload) {
        if (payload.getAppId() == null || payload.getUid() == null || payload.getPermanent() == null) {
            throw new JsonException(OAuthError.INVALID_TOKEN);
        }
    }

    /**
     * 校验 ApiTicket 持久化记录是否有效。
     *
     * @param apiTicket ApiTicket 原文
     * @param payload   ApiTicket 载荷
     */
    private void validatePersistedTicket(String apiTicket, ThirdPartyAppApiTicketPayload payload) {
        this.validateIssuePayload(payload);
        ThirdPartyAppApiTicket ticketRecord = Optional.ofNullable(payload.getJti())
                .flatMap(repository::findByJti)
                .orElseThrow(() -> new JsonException(OAuthError.INVALID_TOKEN));
        if (Boolean.TRUE.equals(ticketRecord.getRevoked())) {
            throw new JsonException(OAuthError.INVALID_TOKEN);
        }
        if (ticketRecord.getExpiredDate() != null && new Date().after(ticketRecord.getExpiredDate())) {
            throw new JsonException(OAuthError.INVALID_TOKEN);
        }
        if (!StringUtils.hasText(ticketRecord.getApiTicket())
                || !Objects.equals(ticketRecord.getApiTicket(), apiTicket)
                || !Objects.equals(ticketRecord.getAppId(), payload.getAppId())
                || !Objects.equals(ticketRecord.getUid(), payload.getUid())
                || !Objects.equals(Boolean.TRUE.equals(ticketRecord.getPermanent()), Boolean.TRUE.equals(payload.getPermanent()))) {
            throw new JsonException(OAuthError.INVALID_TOKEN);
        }
    }

    /**
     * 计算 ApiTicket 的过期时间。
     *
     * @param now       当前时间
     * @param permanent 是否永久票据
     * @return 过期时间，为空时表示永久有效
     */
    private Date calculateExpiredDate(Date now, boolean permanent) {
        if (permanent) {
            return null;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(now);
        calendar.add(Calendar.MINUTE, 15);
        return calendar.getTime();
    }
}
