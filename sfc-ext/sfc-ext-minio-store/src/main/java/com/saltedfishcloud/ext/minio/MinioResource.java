package com.saltedfishcloud.ext.minio;

import com.xiaotao.saltedfishcloud.utils.StringUtils;
import io.minio.GetObjectResponse;
import io.minio.StatObjectResponse;
import lombok.Builder;
import org.springframework.core.io.AbstractResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.ReadableByteChannel;
import java.time.temporal.ChronoField;
import java.util.function.Supplier;

@Builder
public class MinioResource extends AbstractResource {
    private Supplier<GetObjectResponse> getResponseFunc;
    private StatObjectResponse statObjectResponse;

    /**
     * 是否为文件夹
     */
    public boolean isDir() {
        return statObjectResponse.object().endsWith("/") && statObjectResponse.size() == 0;
    }

    /**
     * 获取存储对象名
     */
    public String getObject() {
        return statObjectResponse.object();
    }

    @Override
    public boolean exists() {
        return statObjectResponse != null;
    }

    @Override
    public long contentLength() throws IOException {
        return statObjectResponse.size();
    }

    @Override
    public long lastModified() throws IOException {
        return statObjectResponse.lastModified().getLong(ChronoField.MILLI_OF_SECOND);
    }
    @Override
    public String getFilename() {
        return StringUtils.getURLLastName(statObjectResponse.object());
    }


    @Override
    public String getDescription() {
        return statObjectResponse.object();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return getResponseFunc.get();
    }

    @Override
    public ReadableByteChannel readableChannel() throws IOException {
        return super.readableChannel();
    }
}
