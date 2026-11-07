package com.xiaotao.saltedfishcloud.service;

import com.xiaotao.saltedfishcloud.constant.CacheNames;
import com.sfc.common.constant.SfcUserConstants;
import com.xiaotao.saltedfishcloud.dao.jpa.ProxyInfoRepo;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.xiaotao.saltedfishcloud.utils.http.IgnoreSSLHttpRequestFactory;
import com.xiaotao.saltedfishcloud.model.po.ProxyInfo;
import com.sfc.common.utils.SfcStringUtils;
import com.sfc.cloud.data.validation.SfcUIDValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import com.sfc.cloud.data.service.SfcCrudServiceImpl;

@Service
@Slf4j
public class ProxyInfoServiceImpl extends SfcCrudServiceImpl<ProxyInfo, ProxyInfoRepo> implements ProxyInfoService {
    private final static String LOG_PREFIX = "[代理服务]";

    @Override
    @Cacheable(cacheNames = CacheNames.PROXY_TEST_RESULT, key = "#proxyId", condition = "#useCache")
    @CacheEvict(cacheNames = CacheNames.PROXY_TEST_RESULT, key = "#proxyId", condition = "!#useCache")
    public boolean testProxy(Long proxyId, int timeout, boolean useCache) {
        ProxyInfo proxyInfo = repository.getReferenceById(proxyId);
        proxyInfo.toProxy();

        IgnoreSSLHttpRequestFactory factory = new IgnoreSSLHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        factory.setProxy(proxyInfo.toProxy());
        String testUrl = Optional.ofNullable(proxyInfo.getTestUrl())
                .filter(SfcStringUtils::hasText)
                .orElse("https://www.baidu.com");
        try {
            log.debug("{}使用url：{} 测试连通性: {}", LOG_PREFIX, testUrl, proxyInfo);
            ClientHttpResponse response = factory.createRequest(URI.create(testUrl), HttpMethod.GET).execute();
            return response.getStatusCode().is2xxSuccessful();
        } catch (IOException ignore) {
            return false;
        }
    }

    @Override
    @CacheEvict(cacheNames = CacheNames.PROXY_TEST_RESULT, key = "#entity.id", condition = "true")
    public void save(ProxyInfo entity) {
        super.save(entity);
    }

    @Override
    @CacheEvict(cacheNames = CacheNames.PROXY_TEST_RESULT, key = "#entity.id", condition = "true")
    public void saveWithOwnerPermissions(ProxyInfo entity) {
        super.saveWithOwnerPermissions(entity);
    }

    @Override
    public List<ProxyInfo> listAvailableProxy() {
        List<ProxyInfo> res = new ArrayList<>(findByUid(SfcUserConstants.PUBLIC_USER_ID));
        Long currentUid = SecureUtils.getCurrentUid();
        if (currentUid != null) {
            res.addAll(findByUid(currentUid));
        }
        if (!SfcUIDValidator.validate(SfcUserConstants.PUBLIC_USER_ID, true)) {
            return res.stream()
                    .filter(proxy -> proxy.getUid().equals(SfcUserConstants.PUBLIC_USER_ID) && !Boolean.TRUE.equals(proxy.getIsProtect()))
                    .toList();
        }
        return res;
    }
}
