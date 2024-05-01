package com.xiaotao.saltedfishcloud.common;

import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractGenericHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.http.server.ServletServerHttpResponse;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;

/**
 * 为支持URL重定向的资源提供重定向响应的支持
 */
public class RedirectableUrlHttpMessageConverter extends AbstractGenericHttpMessageConverter<RedirectableUrl> {

    @Override
    public boolean canWrite(Type type, @NotNull Class<?> clazz, MediaType mediaType) {
        return RedirectableUrl.class.isAssignableFrom(clazz);
    }

    @Override
    public boolean canRead(@NotNull Type type, Class<?> contextClass, MediaType mediaType) {
        return false;
    }

    @Override
    protected void writeInternal(RedirectableUrl redirectableUrl, Type type, HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException {
        outputMessage.getHeaders().setLocation(URI.create(redirectableUrl.getRedirectUrl()));
        if (outputMessage instanceof ServletServerHttpResponse) {
            ((ServletServerHttpResponse) outputMessage).getServletResponse().sendRedirect(redirectableUrl.getRedirectUrl());
        }
    }

    @Override
    protected RedirectableUrl readInternal(Class<? extends RedirectableUrl> clazz, HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException {
        return null;
    }

    @Override
    public RedirectableUrl read(Type type, Class<?> contextClass, HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException {
        return null;
    }
}
