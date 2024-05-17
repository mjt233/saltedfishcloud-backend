package com.xiaotao.saltedfishcloud.common;

import com.xiaotao.saltedfishcloud.utils.SpringContextUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.data.util.Lazy;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.*;
import org.springframework.http.server.ServletServerHttpResponse;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.Collection;
import java.util.List;

/**
 * 为支持URL重定向的资源提供重定向响应的支持
 */
public class RedirectableUrlHttpMessageConverter extends AbstractGenericHttpMessageConverter<Object> {
    private final Lazy<ResourceRegionHttpMessageConverter> resourceRegionHttpMessageConverterLazy = Lazy.of(() -> (ResourceRegionHttpMessageConverter)SpringContextUtils.getHttpMessageConverterList().stream()
            .filter(converter -> converter instanceof ResourceRegionHttpMessageConverter)
            .findAny()
            .orElseThrow(() -> new RuntimeException("找不到ResourceRegionHttpMessageConverter")));

    @Override
    public boolean canWrite(Type type, @NotNull Class<?> clazz, MediaType mediaType) {
        // 文件整体请求，不分块的情况
        if(RedirectableUrl.class.isAssignableFrom(clazz)) {
            return true;
        }

        // 文件范围传输，如果类型为列表，则判断列表的泛型是否为ResourceRegion
        if (!List.class.isAssignableFrom(clazz)) {
            return false;
        }
        if (!(type instanceof ParameterizedType)) {
            return false;
        }
        Type[] actualTypeArguments = ((ParameterizedType) type).getActualTypeArguments();
        if (actualTypeArguments.length < 1) {
            return false;
        }
        return ResourceRegion.class.isAssignableFrom((Class<?>) actualTypeArguments[0]);
    }

    @Override
    public boolean canRead(@NotNull Type type, Class<?> contextClass, MediaType mediaType) {
        return false;
    }

    @Override
    protected void writeInternal(Object body, Type type, HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException {
        RedirectableUrl redirectableUrl;
        if (body instanceof RedirectableUrl) {
            // 响应体直接整体就是个RedirectableUrl，好办，直接记录这个RedirectableUrl接口就好
            redirectableUrl = (RedirectableUrl) body;
        } else if (body instanceof List) {
            // 如果响应体是列表，则需要判断列表中的元素是否实现了RedirectableUrl。
            Resource resource = ((ResourceRegion) ((List<?>) body).get(0)).getResource();
            if (resource instanceof RedirectableUrl) {
                // 如果实现了RedirectableUrl，也是直接重定向即可
                redirectableUrl = (RedirectableUrl) resource;
            } else {
                // 如果没有实现RedirectableUrl，则交给Spring默认的ResourceRegionHttpMessageConverter进行处理
                ResourceRegionHttpMessageConverter converter = resourceRegionHttpMessageConverterLazy.get();
                try {
                    Method writeInternalMethod = converter.getClass().getDeclaredMethod("writeInternal", Object.class, Type.class, HttpOutputMessage.class);
                    writeInternalMethod.setAccessible(true);
                    writeInternalMethod.invoke(converter, body, type, outputMessage);
                } catch (Throwable e) {
                    throw new RuntimeException("数据传输转换异常", e);
                }
                return;
            }
        } else {
            // 上面的canWrite方法已经过滤了其他情况，应该不会走到这里
            throw new RuntimeException("不支持" + body.getClass() + "数据类型响应体的url重定向");
        }
        outputMessage.getHeaders().setLocation(URI.create(redirectableUrl.getRedirectUrl()));
        if (outputMessage instanceof ServletServerHttpResponse) {
            ((ServletServerHttpResponse) outputMessage).getServletResponse().sendRedirect(redirectableUrl.getRedirectUrl());
        }
    }

    @Override
    protected Object readInternal(Class<? extends Object> clazz, HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException {
        return null;
    }

    @Override
    public Object read(Type type, Class<?> contextClass, HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException {
        return null;
    }
}
