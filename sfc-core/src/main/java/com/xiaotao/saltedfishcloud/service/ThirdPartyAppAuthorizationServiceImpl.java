package com.xiaotao.saltedfishcloud.service;

import com.xiaotao.saltedfishcloud.dao.jpa.ThirdPartyAppAuthorizationRepo;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyApp;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyAppAuthorization;
import com.xiaotao.saltedfishcloud.model.vo.ThirdPartyAppTokenPayload;
import com.xiaotao.saltedfishcloud.model.vo.ThirdPartyAppUserAuthorizationVo;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyAppAuthorizationService;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyAppService;
import com.xiaotao.saltedfishcloud.utils.JwtUtils;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.xiaotao.saltedfishcloud.validator.annotations.UID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class ThirdPartyAppAuthorizationServiceImpl extends CrudServiceImpl<ThirdPartyAppAuthorization, ThirdPartyAppAuthorizationRepo> implements ThirdPartyAppAuthorizationService {
    private final ThirdPartyAppService thirdPartyAppService;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public ThirdPartyAppUserAuthorizationVo getUserAppAuthorization(Long appId, @UID(value = true) Long uid) {
        return ThirdPartyAppUserAuthorizationVo.builder()
                .thirdPartyApp(thirdPartyAppService.checkAndGetById(appId))
                .authorization(repository.findByAppIdAndUid(appId, uid).orElse(null))
                .build();
    }

    @Override
    public String authorize(Long appId, @UID(value = true) Long uid, String scope) {

        // 保存授权信息
        ThirdPartyApp app = thirdPartyAppService.checkAndGetById(appId);
        ThirdPartyAppAuthorization authorization = repository.findByAppIdAndUid(appId, uid)
                .orElseGet(() -> {
                    ThirdPartyAppAuthorization a = new ThirdPartyAppAuthorization();
                    a.setAppId(appId);
                    a.setUid(uid);
                    return a;
                });

        String newScope = Stream.concat(
                        Arrays.stream(Optional.ofNullable(authorization.getScope()).orElse("").split(" ")),
                        Arrays.stream(scope.split(" "))
                )
                .distinct()
                .collect(Collectors.joining(" "));
        authorization.setScope(newScope);
        save(authorization);

        // 生成授权码
        String code = SecureUtils.getUUID();
        ThirdPartyAppUserAuthorizationVo authorizationVo = ThirdPartyAppUserAuthorizationVo.builder()
                .thirdPartyApp(app)
                .authorization(authorization)
                .build();
        redisTemplate.opsForValue().set(getAuthorizationCodeCacheKey(code), authorizationVo, Duration.ofMinutes(15));

        return code;
    }

    private String getAuthorizationCodeCacheKey(String code) {
        return "oauthApp::authCode::" + code;
    }

    @Override
    public String getAccessToken(String code) {
        ThirdPartyAppUserAuthorizationVo vo = (ThirdPartyAppUserAuthorizationVo) redisTemplate.opsForValue().getAndDelete(getAuthorizationCodeCacheKey(code));
        if (vo == null) {
            throw new JsonException("无效的授权码");
        }
        ThirdPartyAppTokenPayload payload = new ThirdPartyAppTokenPayload();
        payload.setUid(vo.getAuthorization().getUid());
        payload.setScope(vo.getAuthorization().getScope());
        payload.setAppId(vo.getThirdPartyApp().getId());

        return JwtUtils.generateToken(MapperHolder.toJsonNoEx(payload), 0);
    }

    @Override
    public ThirdPartyAppTokenPayload parseAndValidateToken(String token) {
        return null;
    }
}