package com.sfc.ext.webdav.core.propsource;

import com.sfc.ext.webdav.model.resource.WebDavItem;
import com.sfc.ext.webdav.model.resource.WebDavRoot;
import io.milton.http.annotated.AnnoResource;
import io.milton.http.exceptions.BadRequestException;
import io.milton.http.exceptions.NotAuthorizedException;
import io.milton.property.PropertySource;
import io.milton.resource.Resource;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

import javax.xml.namespace.QName;

/**
 * 通用属性源，在原始的PropertySource基础上对Resource类型的参数进行了封装，节省一些类型转换和判断的逻辑
 */
@Slf4j
public abstract class AbstractSfcPropertySource implements PropertySource {
    private static final String LOG_PREFIX = "[WebDAV prop]";

    /**
     * 获取属性值
     * @param name 属性名称
     * @param resource WebDavItem资源对象，如果是根资源则传入null
     */
    public abstract Object getProperty(QName name,@Nullable WebDavItem resource) throws NotAuthorizedException;

    /**
     * 设置属性值
     * @param name 属性名称
     * @param value 属性值
     * @param resource WebDavItem资源对象，如果是根资源则传入null
     */
    public abstract void setProperty(QName name, Object value,@Nullable WebDavItem resource) throws PropertySetException, NotAuthorizedException;

    /**
     * 获取属性元数据
     * @param name 属性名称
     * @param resource WebDavItem资源对象，如果是根资源则传入null
     */
    public abstract PropertyMetaData getPropertyMetaData(QName name,@Nullable WebDavItem resource) throws NotAuthorizedException, BadRequestException;

    /**
     * 清除属性值
     * @param name 属性名称
     * @param resource WebDavItem资源对象，如果是根资源则传入null
     */
    public void clearProperty(QName name,@Nullable WebDavItem resource) throws PropertySetException, NotAuthorizedException {

    }

    /**
     * 获取所有属性名称
     * @param resource WebDavItem资源对象，如果是根资源则传入null
     */
    public abstract java.util.List<QName> getAllPropertyNames(@Nullable WebDavItem resource) throws NotAuthorizedException, BadRequestException;

    /**
     * 判断属性源是否匹配适用处理该属性
     */
    public abstract boolean isMatch(QName name);

    @Override
    public Object getProperty(QName name, Resource r) throws NotAuthorizedException {
        if (!isMatch(name)) {
            return null;
        }
        if (r instanceof AnnoResource a) {
            Object source = a.getSource();
            if (source instanceof WebDavItem wdi) {
                return getProperty(name, wdi);
            }
            if (source instanceof WebDavRoot) {
                return getProperty(name,(WebDavItem)  null);
            }
        }
        log.warn("{} 不支持的Resource类型: {}", LOG_PREFIX, r.getClass());
        return null;
    }

    @Override
    public void setProperty(QName name, Object value, Resource r) throws PropertySetException, NotAuthorizedException {
        if (!isMatch(name)) {
            return;
        }
        if (r instanceof AnnoResource a) {
            Object source = a.getSource();
            if (source instanceof WebDavItem wdi) {
                setProperty(name, value, wdi);
                return;
            }
            if (source instanceof WebDavRoot) {
                setProperty(name, value, (WebDavItem) null);
                return;
            }
        }
        log.warn("{} 不支持的Resource类型: {}", LOG_PREFIX, r.getClass());
    }

    @Override
    public PropertyMetaData getPropertyMetaData(QName name, Resource r) throws NotAuthorizedException, BadRequestException {
        if (!isMatch(name)) {
            return PropertyMetaData.UNKNOWN;
        }
        if (r instanceof AnnoResource a) {
            Object source = a.getSource();
            if (source instanceof WebDavItem wdi) {
                return getPropertyMetaData(name, wdi);
            }
            if (source instanceof WebDavRoot) {
                return getPropertyMetaData(name, (WebDavItem) null);
            }
        }
        log.warn("{} 不支持的Resource类型: {}", LOG_PREFIX, r.getClass());
        return PropertyMetaData.UNKNOWN;
    }

    @Override
    public void clearProperty(QName name, Resource r) throws PropertySetException, NotAuthorizedException {
        if (!isMatch(name)) {
            return;
        }
        if (r instanceof AnnoResource a) {
            Object source = a.getSource();
            if (source instanceof WebDavItem wdi) {
                clearProperty(name, wdi);
                return;
            }
            if (source instanceof WebDavRoot) {
                clearProperty(name, (WebDavItem) null);
                return;
            }
        }
        log.warn("{} 不支持的Resource类型: {}", LOG_PREFIX, r.getClass());
    }

    @Override
    public java.util.List<QName> getAllPropertyNames(Resource r) throws NotAuthorizedException, BadRequestException {
        if (r instanceof AnnoResource a) {
            Object source = a.getSource();
            if (source instanceof WebDavItem wdi) {
                return getAllPropertyNames(wdi);
            }
            if (source instanceof WebDavRoot) {
                return getAllPropertyNames((WebDavItem) null);
            }
        }
        log.warn("{} 不支持的Resource类型: {}", LOG_PREFIX, r.getClass());
        return java.util.List.of();
    }
}
