package com.xiaotao.saltedfishcloud.service;

import com.xiaotao.saltedfishcloud.cache.CacheKeyPrefixes;
import com.xiaotao.saltedfishcloud.cache.CacheService;
import com.xiaotao.saltedfishcloud.constant.error.OAuthError;
import com.xiaotao.saltedfishcloud.dao.jpa.ThirdPartyAppTokenRepo;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyApp;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyAppAuthorization;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyAppToken;
import com.xiaotao.saltedfishcloud.model.vo.ThirdPartyAppApiTicketPayload;
import com.xiaotao.saltedfishcloud.model.vo.ThirdPartyAppUserAuthorizationVo;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyAppAuthorizationService;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyAppApiTicketService;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyAppKeyService;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyAppService;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyAppTokenService;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Calendar;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class ThirdPartyAppTokenServiceImpl extends CrudServiceImpl<ThirdPartyAppToken, ThirdPartyAppTokenRepo> implements ThirdPartyAppTokenService {
    private final ThirdPartyAppAuthorizationService authorizationService;
    private final ThirdPartyAppService appService;
    private final ThirdPartyAppKeyService keyService;
    private final CacheService cacheService;
    private final ThirdPartyAppApiTicketService thirdPartyAppApiTicketService;


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
    public String getApiTicket(Long appId, Long uid, String accessToken, boolean permanent, boolean revokeOlder) {
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
        ThirdPartyAppApiTicketPayload apiTicketPayload = ThirdPartyAppApiTicketPayload.builder()
                .appId(tokenRecord.getAppId())
                .uid(tokenRecord.getUid())
                .scope(authorization.getScope())
                .permanent(permanent)
                .build();
        return thirdPartyAppApiTicketService.issue(apiTicketPayload, revokeOlder);
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

    private String getAuthorizationCodeCacheKey(String code) {
        return CacheKeyPrefixes.OAUTH_AUTH_CODE + code;
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
        thirdPartyAppApiTicketService.revokeByAppIdAndUid(appId, uid);

        // 移除Token
        Optional.ofNullable(tokenRecord)
                .map(ThirdPartyAppToken::getId)
                .ifPresent(this::delete);

        // 移除授权记录
        authorizationService.revoke(appId, uid);
    }
}
