package com.xiaotao.saltedfishcloud.service;

import com.xiaotao.saltedfishcloud.dao.jpa.ThirdPartyAppKeyRepo;
import com.xiaotao.saltedfishcloud.event.oauth.ThirdPartyAppDeleteEvent;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyApp;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyAppKey;
import com.xiaotao.saltedfishcloud.model.vo.ThirdPartyAppKeyVo;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyAppKeyService;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyAppService;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import com.xiaotao.saltedfishcloud.utils.db.JpaLambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.lang.Nullable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Objects;

@RequiredArgsConstructor
@Service
public class ThirdPartyAppKeyServiceImpl extends CrudServiceImpl<ThirdPartyAppKey, ThirdPartyAppKeyRepo> implements ThirdPartyAppKeyService {
    private final ThirdPartyAppService appService;

    @Override
    public List<ThirdPartyAppKeyVo> listKeyByAppId(Long appId) {
        return getRepository().findAll(JpaLambdaQueryWrapper.get(ThirdPartyAppKey.class).eq(ThirdPartyAppKey::getAppId, appId).build())
                .stream()
                .map(k -> {
                    ThirdPartyAppKeyVo vo = new ThirdPartyAppKeyVo();
                    vo.setAppId(k.getAppId());
                    vo.setName(k.getName());
                    vo.setClientSecret(k.getClientSecretMaskValue());
                    vo.setRemark(k.getRemark());

                    vo.setId(k.getId());
                    vo.setCreateAt(k.getCreateAt());
                    vo.setUpdateAt(k.getUpdateAt());
                    vo.setUid(k.getUid());
                    return vo;
                })
                .toList();
    }

    @Override
    public void deleteByAppId(Collection<Long> appIds) {
        repository.deleteAll(repository.findAll(JpaLambdaQueryWrapper.get(ThirdPartyAppKey.class)
                        .in(ThirdPartyAppKey::getAppId, appIds)
                .build()
        ));
    }

    @EventListener(ThirdPartyAppDeleteEvent.class)
    private void handleAppDeleteEvent(ThirdPartyAppDeleteEvent e) {
        deleteByAppId(e.getApps().stream().map(ThirdPartyApp::getId).toList());
    }

    @Override
    public ThirdPartyAppKeyVo generateNewKey(Long appId, @Nullable String name) {
        Objects.requireNonNull(appService.findById(appId), "appId无效");
        String rawKey = SecureUtils.getUUID();
        ThirdPartyAppKey key = new ThirdPartyAppKey();
        key.setName(StringUtils.hasText(name) ? name : "Default");
        key.setAppId(appId);
        key.setUid(SecureUtils.getCurrentUid());
        key.setClientSecretHash(SecureUtils.getBCryptPasswordEncoder().encode(rawKey));
        key.setClientSecretMaskValue(generateSecretMaskValue(rawKey));
        this.save(key);

        ThirdPartyAppKeyVo vo = new ThirdPartyAppKeyVo();
        vo.setAppId(appId);
        vo.setClientSecret(rawKey);
        vo.setName(key.getName());
        return vo;
    }

    @Override
    public boolean validate(Long appId, String clientSecret) {
        return getRepository().findByAppId(appId)
                .stream()
                .anyMatch(k -> SecureUtils.getBCryptPasswordEncoder().matches(clientSecret, k.getClientSecretHash()));
    }

    @Override
    public void changeKeyInfo(ThirdPartyAppKeyVo thirdPartyAppKeyVo) {
        ThirdPartyAppKeyRepo repo = getRepository();
        ThirdPartyAppKey key = repo.findById(thirdPartyAppKeyVo.getId()).orElseThrow(() -> new JsonException("无效的密钥id"));
        key.setName(thirdPartyAppKeyVo.getName());
        key.setRemark(thirdPartyAppKeyVo.getRemark());
        key.setUpdateAt(new Date());
        repo.save(key);
    }

    /**
     * 将密钥转为遮掩值，最多只保留头尾各6个字符，其他部分全部转为*
     * @param secret    密钥
     * @return          转换结果
     */
    private String generateSecretMaskValue(String secret) {
        if (secret == null || secret.length() <= 12) {
            return "********";
        }
        
        int length = secret.length();
        String start = secret.substring(0, 6);
        String end = secret.substring(length - 6);
        String middle = "*".repeat(length - 12);
        
        return start + middle + end;
    }
}