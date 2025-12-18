package com.xiaotao.saltedfishcloud.service.resource;

import com.xiaotao.saltedfishcloud.helper.OutputStreamConsumer;
import com.xiaotao.saltedfishcloud.model.dto.ResourceRequest;

import java.io.IOException;
import java.io.OutputStream;

/**
 * 可写入数据的资源协议处理器
 * @param <T>   从原始资源请求中解析出的特定的ProtocolHandler的参数
 */
public abstract class AbstractWritableResourceProtocolHandler<T> extends AbstractResourceProtocolHandler<T> {

    /**
     * 是否支持缩略图写入
     * @param resourceRequest   原始资源请求
     * @param param 原始资源请求解析后的参数
     */
    public boolean isSupportedThumbnailWrite(ResourceRequest resourceRequest, T param) {
        return false;
    }


    @Override
    public boolean isWriteable() {
        return true;
    }

    /**
     * 处理文件接收逻辑
     * @param resourceRequest   原始请求参数
     * @param parsedParam   解析后的参数
     * @param streamConsumer    文件输出流处理函数
     */
    public abstract void handleWriteResource(ResourceRequest resourceRequest, T parsedParam, OutputStreamConsumer<OutputStream> streamConsumer) throws IOException;

    @Override
    public void writeResource(ResourceRequest resourceRequest, OutputStreamConsumer<OutputStream> streamConsumer) throws IOException {
        T parsedParam = validAndParseParam(resourceRequest, true);
        if (Boolean.TRUE.equals(resourceRequest.getIsThumbnail()) && this.isSupportedThumbnailWrite(resourceRequest, parsedParam)) {
            throw new IllegalArgumentException("不支持写入缩略图");
        }
        handleWriteResource(resourceRequest, parsedParam, streamConsumer);
    }
}
