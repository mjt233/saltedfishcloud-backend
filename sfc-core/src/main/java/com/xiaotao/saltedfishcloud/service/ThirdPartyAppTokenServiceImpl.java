package com.xiaotao.saltedfishcloud.service;

import com.xiaotao.saltedfishcloud.constant.error.OAuthError;
import com.xiaotao.saltedfishcloud.dao.jpa.ThirdPartyAppTokenRepo;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyApp;
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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Calendar;
import java.util.Date;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ThirdPartyAppTokenServiceImpl extends CrudServiceImpl<ThirdPartyAppToken, ThirdPartyAppTokenRepo> implements ThirdPartyAppTokenService {
    private final ThirdPartyAppAuthorizationService authorizationService;
    private final ThirdPartyAppService appService;
    private final ThirdPartyAppKeyService keyService;
    private final RedisTemplate<String, Object> redisTemplate;


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
        redisTemplate.opsForValue().set(getAuthorizationCodeCacheKey(code), authorizationVo, Duration.ofMinutes(15));
        return code;
    }

    @Override
    public String getApiTicket(Long appId, Long uid, String accessToken) {
        Date now = new Date();
        // 获取 Access Token 对应的用户授权信息
        ThirdPartyAppToken tokenRecord = Optional.ofNullable(repository.findByAppIdAndUid(appId, uid))
                .filter(t -> SecureUtils.getBCryptPasswordEncoder().matches(accessToken, t.getAccessToken()) && now.before(t.getAccessTokenExpiredDate()) )
                .orElseThrow(() -> new JsonException(OAuthError.INVALID_TOKEN));
        ThirdPartyAppUserAuthorizationVo authorizationVo = authorizationService.getUserAppAuthorization(tokenRecord.getAppId(), tokenRecord.getUid());
        String originApiTicket = tokenRecord.getApiTicket();

        // 生成JWT凭证作为 Api Ticket
        String apiTicket = generateApiTicket(ThirdPartyAppApiTicketPayload.builder()
                .appId(tokenRecord.getAppId())
                .uid(tokenRecord.getUid())
                .scope(authorizationVo.getAuthorization().getScope())
                .jti(IdUtil.getId())
                .build(), (int) Duration.ofMinutes(15).toSeconds());
        tokenRecord.setApiTicket(apiTicket);
        save(tokenRecord);

        // 将未过期的原凭证拉入黑名单
        if (StringUtils.hasText(originApiTicket) && !JwtUtils.checkIsExpired(originApiTicket)) {
            this.disableApiTicket(originApiTicket);
        }
        return apiTicket;
    }

    private void disableApiTicket(String apiTicket) {
        redisTemplate.opsForValue().set("oauthApp::disabledTicket::" + SecureUtils.getMd5(apiTicket), 1, Duration.ofMinutes(15));
    }

    private void checkApiTicketIsDisabled(String apiTicket) {
        if(redisTemplate.opsForValue().get("oauthApp::disabledTicket::" + SecureUtils.getMd5(apiTicket)) != null) {
            throw new JsonException(OAuthError.INVALID_TOKEN);
        }
    }

    @Override
    public ThirdPartyAppApiTicketPayload parseAndValidateApiTicket(String apiTicket) {
        try {
            this.checkApiTicketIsDisabled(apiTicket);
            return MapperHolder.parseJson(JwtUtils.parse(apiTicket), ThirdPartyAppApiTicketPayload.class);
        } catch (IOException e) {
            throw new JsonException(OAuthError.INVALID_TOKEN);
        }
    }

    private String getAuthorizationCodeCacheKey(String code) {
        return "oauthApp::authCode::" + code;
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
        ThirdPartyAppUserAuthorizationVo vo = (ThirdPartyAppUserAuthorizationVo) redisTemplate.opsForValue().get(codeKey);
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
        redisTemplate.delete(codeKey);
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

        // 移除Token
        Optional.ofNullable(tokenRecord)
                .map(ThirdPartyAppToken::getId)
                .ifPresent(this::delete);

        // 移除授权记录
        authorizationService.revoke(appId, uid);
    }
}
