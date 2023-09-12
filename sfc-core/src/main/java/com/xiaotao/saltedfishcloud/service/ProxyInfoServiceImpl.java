package com.xiaotao.saltedfishcloud.service;

import com.sfc.common.service.CrudServiceImpl;
import com.sfc.constant.CacheNames;
import com.xiaotao.saltedfishcloud.dao.jpa.ProxyInfoRepo;
import com.xiaotao.saltedfishcloud.download.IgnoreSSLHttpRequestFactory;
import com.xiaotao.saltedfishcloud.model.po.ProxyInfo;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.Optional;

@Service
@Slf4j
public class ProxyInfoServiceImpl extends CrudServiceImpl<ProxyInfo, ProxyInfoRepo> implements ProxyInfoService {
    private final static String LOG_PREFIX = "[代理服务]";

    @Override
    @Cacheable(cacheNames = CacheNames.PROXY_TEST_RESULT, key = "#proxyId", condition = "#useCache")
    @CacheEvict(cacheNames = CacheNames.PROXY_TEST_RESULT, key = "#proxyId", condition = "!#useCache")
    public boolean testProxy(Long proxyId, int timeout, boolean useCache) {
        ProxyInfo proxyInfo = repository.getOne(proxyId);
        proxyInfo.toProxy();

        IgnoreSSLHttpRequestFactory factory = new IgnoreSSLHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        factory.setProxy(proxyInfo.toProxy());
        String testUrl = Optional.ofNullable(proxyInfo.getTestUrl())
                .filter(StringUtils::hasText)
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
}
