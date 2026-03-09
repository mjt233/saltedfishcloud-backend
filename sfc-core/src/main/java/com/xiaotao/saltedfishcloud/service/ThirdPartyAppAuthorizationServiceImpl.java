package com.xiaotao.saltedfishcloud.service;

import com.xiaotao.saltedfishcloud.dao.jpa.ThirdPartyAppAuthorizationRepo;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyAppAuthorization;
import com.xiaotao.saltedfishcloud.model.vo.ThirdPartyAppUserAuthorizationVo;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyAppAuthorizationService;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyAppService;
import com.xiaotao.saltedfishcloud.validator.annotations.UID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class ThirdPartyAppAuthorizationServiceImpl extends CrudServiceImpl<ThirdPartyAppAuthorization, ThirdPartyAppAuthorizationRepo> implements ThirdPartyAppAuthorizationService {
    private final ThirdPartyAppService thirdPartyAppService;

    @Override
    public ThirdPartyAppUserAuthorizationVo getUserAppAuthorization(Long appId, @UID(value = true) Long uid) {
        return ThirdPartyAppUserAuthorizationVo.builder()
                .thirdPartyApp(thirdPartyAppService.checkAndGetById(appId))
                .authorization(repository.findByAppIdAndUid(appId, uid).orElse(null))
                .build();
    }

    @Override
    public ThirdPartyAppAuthorization authorize(Long appId, @UID(value = true) Long uid, String scope) {
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
        return authorization;
    }

    @Override
    public void revoke(Long appId, Long uid) {
        repository.deleteByAppIdAndUid(appId, uid);
    }
}