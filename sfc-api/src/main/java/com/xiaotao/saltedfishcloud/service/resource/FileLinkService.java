package com.xiaotao.saltedfishcloud.service.resource;

import com.xiaotao.saltedfishcloud.model.dto.ResourceRequest;
import org.springframework.core.io.Resource;

import java.io.IOException;

/**
 * 文件临时链接服务。
 */
public interface FileLinkService {

    /**
     * 创建一个可直接访问的临时文件链接。
     *
     * @param baseUrl         下载接口的基础 URL
     * @param resourceRequest 资源请求参数
     * @return 完整下载链接
     */
    String createLink(String baseUrl, ResourceRequest resourceRequest);

    /**
     * 解析完整下载链接并返回对应资源。
     *
     * @param url 创建链接返回的完整 URL
     * @return 对应的资源对象
     * @throws IOException 读取资源失败时抛出
     */
    Resource parseLink(String url) throws IOException;
}

