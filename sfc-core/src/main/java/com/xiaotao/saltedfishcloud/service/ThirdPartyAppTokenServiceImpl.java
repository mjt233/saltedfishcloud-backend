package com.xiaotao.saltedfishcloud.service;

import com.xiaotao.saltedfishcloud.cache.CacheKeyPrefixes;
import com.xiaotao.saltedfishcloud.cache.CacheService;
import com.xiaotao.saltedfishcloud.constant.error.OAuthError;
import com.xiaotao.saltedfishcloud.dao.jpa.ThirdPartyAppApiTicketRepo;
import com.xiaotao.saltedfishcloud.dao.jpa.ThirdPartyAppTokenRepo;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyApp;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyAppApiTicket;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyAppAuthorization;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyAppToken;
import com.xiaotao.saltedfishcloud.model.vo.ThirdPartyAppApiTicketPayload;
import com.xiaotao.saltedfishcloud.model.vo.ThirdPartyAppUserAuthorizationVo;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyAppAuthorizationService;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyAppKeyService;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyAppService;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyAppTokenService;
import com.xiaotao.saltedfishcloud.utils.JwtUtils;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import com.xiaotao.saltedfishcloud.utils.identifier.IdUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Calendar;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class ThirdPartyAppTokenServiceImpl extends CrudServiceImpl<ThirdPartyAppToken, ThirdPartyAppTokenRepo> implements ThirdPartyAppTokenService {
    private final ThirdPartyAppAuthorizationService authorizationService;
    private final ThirdPartyAppService appService;
    private final ThirdPartyAppKeyService keyService;
    private final CacheService cacheService;
    private final ThirdPartyAppApiTicketRepo thirdPartyAppApiTicketRepo;


    @Override
    public String authorize(Long appId, Long uid, String scope) {
        // 保存授权信息
        ThirdPartyApp app = appService.checkAndGetById(appId);
        ThirdPartyAppAuthorization authorizeResult = authorizationService.authorize(appId, uid, scope);

        String code = SecureUtils.getUUID();
        ThirdPartyAppUserAuthorizationVo authorizationVo = ThirdPartyAppUserAuthorizationVo.builder()
                .thirdPartyApp(app)
                .authorization(authorizeResult)
                .build();
        cacheService.set(getAuthorizationCodeCacheKey(code), authorizationVo, 15, TimeUnit.MINUTES);
        return code;
    }

    @Override
    public String getApiTicket(Long appId, Long uid, String accessToken, boolean permanent) {
        Date now = new Date();
        // 获取 Access Token 对应的用户授权信息
        ThirdPartyAppToken tokenRecord = Optional.ofNullable(repository.findByAppIdAndUid(appId, uid))
                .filter(t -> SecureUtils.getBCryptPasswordEncoder().matches(accessToken, t.getAccessToken()) && now.before(t.getAccessTokenExpiredDate()) )
                .orElseThrow(() -> new JsonException(OAuthError.INVALID_TOKEN));
        ThirdPartyAppUserAuthorizationVo authorizationVo = authorizationService.getUserAppAuthorization(tokenRecord.getAppId(), tokenRecord.getUid());
        ThirdPartyAppAuthorization authorization = Optional.ofNullable(authorizationVo.getAuthorization())
                .orElseThrow(() -> new JsonException(OAuthError.PERMISSION_DENIED));
        ThirdPartyApp app = authorizationVo.getThirdPartyApp();
        this.checkPermanentApiTicketPermission(app, permanent);
        this.revokeSameTypeApiTickets(tokenRecord.getAppId(), tokenRecord.getUid(), permanent);

        // 生成JWT凭证作为 Api Ticket
        ThirdPartyAppApiTicketPayload apiTicketPayload = ThirdPartyAppApiTicketPayload.builder()
                .appId(tokenRecord.getAppId())
                .uid(tokenRecord.getUid())
                .scope(authorization.getScope())
                .permanent(permanent)
                .jti(IdUtil.getId())
                .build();
        String apiTicket = generateApiTicket(apiTicketPayload, permanent ? 0 : (int) TimeUnit.MINUTES.toSeconds(15));
        tokenRecord.setApiTicket(apiTicket);
        save(tokenRecord);

        ThirdPartyAppApiTicket apiTicketRecord = new ThirdPartyAppApiTicket();
        apiTicketRecord.setAppId(tokenRecord.getAppId());
        apiTicketRecord.setUid(tokenRecord.getUid());
        apiTicketRecord.setJti(apiTicketPayload.getJti());
        apiTicketRecord.setPermanent(permanent);
        apiTicketRecord.setExpiredDate(this.calculateApiTicketExpiredDate(now, permanent));
        apiTicketRecord.setRevoked(false);
        thirdPartyAppApiTicketRepo.save(apiTicketRecord);
        return apiTicket;
    }

    /**
     * 校验应用是否允许申请永久有效的 ApiTicket。
     *
     * @param app       第三方应用配置
     * @param permanent 是否申请永久票据
     */
    private void checkPermanentApiTicketPermission(ThirdPartyApp app, boolean permanent) {
        if (permanent && !Boolean.TRUE.equals(app.getAllowPermanentApiTicket())) {
            throw new JsonException(OAuthError.PERMANENT_API_TICKET_NOT_ALLOWED);
        }
    }

    /**
     * 计算 ApiTicket 的过期时间。
     *
     * @param now       当前时间
     * @param permanent 是否永久票据
     * @return 过期时间，为空时表示永久有效
     */
    private Date calculateApiTicketExpiredDate(Date now, boolean permanent) {
        if (permanent) {
            return null;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(now);
        calendar.add(Calendar.MINUTE, 15);
        return calendar.getTime();
    }

    /**
     * 撤销指定应用指定用户指定类型的历史 ApiTicket。
     *
     * @param appId     第三方应用id
     * @param uid       用户id
     * @param permanent 是否永久票据
     */
    private void revokeSameTypeApiTickets(Long appId, Long uid, boolean permanent) {
        thirdPartyAppApiTicketRepo.revokeByAppIdAndUidAndPermanent(appId, uid, permanent);
    }

    /**
     * 使指定 ApiTicket 失效。
     *
     * @param apiTicket 待失效的 ApiTicket
     */
    private void disableApiTicket(String apiTicket) {
        ThirdPartyAppApiTicketPayload payload = this.tryParseApiTicketPayload(apiTicket);
        if (payload != null && payload.getJti() != null) {
            Optional<ThirdPartyAppApiTicket> ticketRecord = thirdPartyAppApiTicketRepo.findByJti(payload.getJti());
            if (ticketRecord.isPresent()) {
                ThirdPartyAppApiTicket record = ticketRecord.get();
                if (!Boolean.TRUE.equals(record.getRevoked())) {
                    record.setRevoked(true);
                    thirdPartyAppApiTicketRepo.save(record);
                }
                return;
            }
        }
        this.disableLegacyApiTicket(apiTicket, payload);
    }

    /**
     * 将旧版 ApiTicket 拉入缓存黑名单。
     *
     * @param apiTicket 待失效的 ApiTicket
     * @param payload   ApiTicket 载荷，解析失败时允许为空
     */
    private void disableLegacyApiTicket(String apiTicket, ThirdPartyAppApiTicketPayload payload) {
        String key = CacheKeyPrefixes.OAUTH_DISABLED_TICKET + SecureUtils.getMd5(apiTicket);
        if (payload != null && Boolean.TRUE.equals(payload.getPermanent())) {
            cacheService.set(key, 1);
            return;
        }
        if (!JwtUtils.checkIsExpired(apiTicket)) {
            cacheService.set(key, 1, 15, TimeUnit.MINUTES);
        }
    }

    /**
     * 尝试解析 ApiTicket 载荷。
     *
     * @param apiTicket ApiTicket 原文
     * @return 解析成功时返回载荷，失败时返回 {@code null}
     */
    private ThirdPartyAppApiTicketPayload tryParseApiTicketPayload(String apiTicket) {
        try {
            return MapperHolder.parseJson(JwtUtils.parse(apiTicket), ThirdPartyAppApiTicketPayload.class);
        } catch (Exception e) {
            return null;
        }
    }

    private void checkApiTicketIsDisabled(String apiTicket) {
        if(cacheService.get(CacheKeyPrefixes.OAUTH_DISABLED_TICKET + SecureUtils.getMd5(apiTicket)) != null) {
            throw new JsonException(OAuthError.INVALID_TOKEN);
        }
    }

    @Override
    public ThirdPartyAppApiTicketPayload parseAndValidateApiTicket(String apiTicket) {
        try {
            ThirdPartyAppApiTicketPayload payload = MapperHolder.parseJson(JwtUtils.parse(apiTicket), ThirdPartyAppApiTicketPayload.class);
            this.validateApiTicketRecord(apiTicket, payload);
            return payload;
        } catch (JsonException | IOException e) {
            throw new JsonException(OAuthError.INVALID_TOKEN);
        }
    }

    /**
     * 校验 ApiTicket 持久化记录是否有效。
     *
     * @param apiTicket ApiTicket 原文
     * @param payload   ApiTicket 载荷
     */
    private void validateApiTicketRecord(String apiTicket, ThirdPartyAppApiTicketPayload payload) {
        Optional<ThirdPartyAppApiTicket> ticketRecordOptional = Optional.ofNullable(payload.getJti())
                .flatMap(thirdPartyAppApiTicketRepo::findByJti);
        if (ticketRecordOptional.isEmpty()) {
            if (payload.getPermanent() != null) {
                throw new JsonException(OAuthError.INVALID_TOKEN);
            }
            this.checkApiTicketIsDisabled(apiTicket);
            return;
        }
        ThirdPartyAppApiTicket ticketRecord = ticketRecordOptional.get();
        if (Boolean.TRUE.equals(ticketRecord.getRevoked())) {
            throw new JsonException(OAuthError.INVALID_TOKEN);
        }
        if (ticketRecord.getExpiredDate() != null && new Date().after(ticketRecord.getExpiredDate())) {
            throw new JsonException(OAuthError.INVALID_TOKEN);
        }
        if (!Objects.equals(ticketRecord.getAppId(), payload.getAppId())
                || !Objects.equals(ticketRecord.getUid(), payload.getUid())
                || !Objects.equals(Boolean.TRUE.equals(ticketRecord.getPermanent()), Boolean.TRUE.equals(payload.getPermanent()))) {
            throw new JsonException(OAuthError.INVALID_TOKEN);
        }
    }

    private String getAuthorizationCodeCacheKey(String code) {
        return CacheKeyPrefixes.OAUTH_AUTH_CODE + code;
    }

    /**
     * 生成第三方应用使用的Token
     *
     * @param payload token载荷数据
     * @param expr    有效时长，<=0表示无限
     */
    private String generateApiTicket(Object payload, int expr) {
        return JwtUtils.generateToken(MapperHolder.toJsonNoEx(payload), expr);
    }

    private String generateAccessToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32]; // 256 bits
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Override
    public String getAccessToken(String code, String clientSecret) {
        // 验证授权码是否有效，并解析授权码
        String codeKey = getAuthorizationCodeCacheKey(code);
        ThirdPartyAppUserAuthorizationVo vo = cacheService.get(codeKey);
        if (vo == null) {
            throw new JsonException(OAuthError.INVALID_CODE);
        }

        // 验证第三方OAuth应用客户端密钥是否正确
        Long appId = vo.getThirdPartyApp().getId();
        if(!keyService.validate(appId, clientSecret)) {
            throw new JsonException(OAuthError.CLIENT_SECRET_INVALID);
        }

        // 创建Access Token
        Long uid = vo.getAuthorization().getUid();
        String accessToken = this.generateAccessToken();
        ThirdPartyAppToken tokenRecord = Optional.ofNullable(repository.findByAppIdAndUid(appId, uid))
                .orElseGet(() -> {
                    ThirdPartyAppToken newTokenRecord = new ThirdPartyAppToken();
                    newTokenRecord.setAppId(appId);
                    newTokenRecord.setUid(uid);
                    return newTokenRecord;
                });
        tokenRecord.setAccessToken(SecureUtils.getBCryptPasswordEncoder().encode(accessToken));
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.add(Calendar.DAY_OF_YEAR, 90);
        tokenRecord.setAccessTokenExpiredDate(calendar.getTime());

        // 保存Access Token
        save(tokenRecord);

        // 移除授权码有效缓存
        cacheService.delete(codeKey);
        return accessToken;
    }

    @Override
    @Transactional
    public void revoke(Long appId, Long uid) {
        ThirdPartyAppToken tokenRecord = repository.findByAppIdAndUid(appId, uid);
        // 失效原凭证
        Optional.ofNullable(tokenRecord)
                .map(ThirdPartyAppToken::getApiTicket)
                .filter(StringUtils::hasText)
                .ifPresent(this::disableApiTicket);
        thirdPartyAppApiTicketRepo.revokeByAppIdAndUid(appId, uid);

        // 移除Token
        Optional.ofNullable(tokenRecord)
                .map(ThirdPartyAppToken::getId)
                .ifPresent(this::delete);

        // 移除授权记录
        authorizationService.revoke(appId, uid);
    }
}
