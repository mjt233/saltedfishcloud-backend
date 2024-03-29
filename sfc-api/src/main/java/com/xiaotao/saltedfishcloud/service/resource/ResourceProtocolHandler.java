package com.xiaotao.saltedfishcloud.service.resource;

import com.xiaotao.saltedfishcloud.model.dto.ResourceRequest;
import org.springframework.core.io.Resource;

import java.io.IOException;


/**
 * 资源协议操作器，用于统一各个文件资源的操作。
 */
public interface ResourceProtocolHandler {

    /**
     * 根据参数获取文件资源
     * @param param     资源请求参数
     * @return          文件资源，若无法获取则返回null，支持资源重定向
     */
    Resource getFileResource(ResourceRequest param) throws IOException;


    /**
     * 获取支持的资源协议名
     */
    String getProtocolName();

    /**
     * 是否支持写入资源
     */
    default boolean isWriteable() {
        return false;
    }

    /**
     * 执行资源写入
     * @param param     资源定位请求参数
     * @param resource  待写入资源
     */
    default void writeResource(ResourceRequest param, Resource resource) throws IOException {

    }


}
