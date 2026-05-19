package com.xiaotao.saltedfishcloud.service;

import com.xiaotao.saltedfishcloud.cache.CacheKeyPrefixes;
import com.xiaotao.saltedfishcloud.cache.CacheService;
import com.xiaotao.saltedfishcloud.constant.error.OAuthError;
import com.xiaotao.saltedfishcloud.dao.jpa.ThirdPartyAppTokenRepo;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyApp;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyAppAuthorization;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyAppToken;
import com.xiaotao.saltedfishcloud.model.vo.ThirdPartyAppAccessTokenPayload;
import com.xiaotao.saltedfishcloud.model.vo.ThirdPartyAppApiTicketPayload;
import com.xiaotao.saltedfishcloud.model.vo.ThirdPartyAppUserAuthorizationVo;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyAppAuthorizationService;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyAppApiTicketService;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyAppKeyService;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyAppService;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyAppTokenService;
import com.xiaotao.saltedfishcloud.utils.JwtUtils;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
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
    public String getApiTicket(String accessToken, boolean permanent, boolean revokeOlder) {
        Date now = new Date();
        ThirdPartyAppAccessTokenPayload accessTokenPayload = this.parseAccessToken(accessToken);
        String accessTokenFingerprint = this.getAccessTokenFingerprint(accessToken);

        // 获取 Access Token 对应的用户授权信息
        ThirdPartyAppToken tokenRecord = Optional.ofNullable(repository.findByAppIdAndUid(accessTokenPayload.getAppId(), accessTokenPayload.getUid()))
                .filter(t -> SecureUtils.getBCryptPasswordEncoder().matches(accessTokenFingerprint, t.getAccessToken()))
                .filter(t -> t.getAccessTokenExpiredDate() == null || now.before(t.getAccessTokenExpiredDate()))
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

    /**
     * 解析并校验第三方 Access Token 中的应用与用户上下文。
     *
     * @param accessToken Access Token 原文
     * @return Access Token 载荷
     */
    private ThirdPartyAppAccessTokenPayload parseAccessToken(String accessToken) {
        try {
            ThirdPartyAppAccessTokenPayload payload = MapperHolder.parseJson(JwtUtils.parse(accessToken), ThirdPartyAppAccessTokenPayload.class);
            if (payload.getAppId() == null || payload.getUid() == null || payload.getTokenId() == null) {
                throw new JsonException(OAuthError.INVALID_TOKEN);
            }
            return payload;
        } catch (JsonException | IOException e) {
            throw new JsonException(OAuthError.INVALID_TOKEN);
        }
    }

    private String getAuthorizationCodeCacheKey(String code) {
        return CacheKeyPrefixes.OAUTH_AUTH_CODE + code;
    }

    /**
     * 计算 Access Token 的固定长度指纹。
     * <p>
     * 由于 JWT 形式的 Access Token 可能超过 BCrypt 允许的 72 字节输入长度，
     * 因此在持久化与匹配前先转为 SHA-256 摘要，再交由 BCrypt 处理。
     *
     * @param accessToken Access Token 原文
     * @return 固定长度 Access Token 指纹
     */
    private String getAccessTokenFingerprint(String accessToken) {
        return SecureUtils.getSha256(accessToken);
    }

    /**
     * 生成内嵌应用与用户上下文的 Access Token。
     *
     * @param appId 第三方 OAuth 应用 ID
     * @param uid   系统用户 ID
     * @return Access Token 原文
     */
    private String generateAccessToken(Long appId, Long uid) {
        ThirdPartyAppAccessTokenPayload payload = ThirdPartyAppAccessTokenPayload.builder()
                .appId(appId)
                .uid(uid)
                .tokenId(SecureUtils.getUUID())
                .build();
        return JwtUtils.generateToken(MapperHolder.toJsonNoEx(payload), -1);
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
        String accessToken = this.generateAccessToken(appId, uid);
        String accessTokenFingerprint = this.getAccessTokenFingerprint(accessToken);
        ThirdPartyAppToken tokenRecord = Optional.ofNullable(repository.findByAppIdAndUid(appId, uid))
                .orElseGet(() -> {
                    ThirdPartyAppToken newTokenRecord = new ThirdPartyAppToken();
                    newTokenRecord.setAppId(appId);
                    newTokenRecord.setUid(uid);
                    return newTokenRecord;
                });

        tokenRecord.setAccessToken(SecureUtils.getBCryptPasswordEncoder().encode(accessTokenFingerprint));

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
