package com.sfc.ext.webdav.core.propsource;

import com.sfc.ext.webdav.model.resource.WebDavItem;
import com.xiaotao.saltedfishcloud.model.param.FileTimeAttribute;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.SpringContextUtils;
import io.milton.http.Response;
import io.milton.http.exceptions.BadRequestException;
import io.milton.http.exceptions.NotAuthorizedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.util.Lazy;

import javax.xml.namespace.QName;
import java.io.IOException;
import java.util.Date;
import java.util.List;

@Slf4j
public class Win32DatePropertySource extends AbstractSfcPropertySource {
    private final static String CREATION_TIME = "Win32CreationTime";
    private final static String LAST_MODIFIED_TIME = "Win32LastModifiedTime";
    private final Lazy<DiskFileSystemManager> diskFileSystemManagerLazy = Lazy.of(() -> SpringContextUtils.getContext().getBean(DiskFileSystemManager.class));

    @Override
    public Object getProperty(QName name, WebDavItem resource) throws NotAuthorizedException {
        if (resource == null) {
            return null;
        }
        if (name.getLocalPart().equals(CREATION_TIME)) {
            return resource.getCreateDate();
        }
        if (name.getLocalPart().equals(LAST_MODIFIED_TIME)) {
            return resource.getModifiedDate();
        }
        return null;
    }

    @Override
    public void setProperty(QName name, Object value, WebDavItem resource) throws PropertySetException, NotAuthorizedException {
        if (!(value instanceof Date d)) {
            log.warn("无法通过WebDAV更新文件日期属性，value不是Date类型，而是: {}", value.getClass());
            return;
        }
        try {
            FileTimeAttribute attribute = new FileTimeAttribute();
            if (name.getLocalPart().equals(CREATION_TIME)) {
                resource.setCreateDate(d);
                attribute.setCreateTime(d);
            } else if (name.getLocalPart().equals(LAST_MODIFIED_TIME)) {
                resource.setModifiedDate(d);
                attribute.setModifyTime(d);
            }
            diskFileSystemManagerLazy.get()
                    .getMainFileSystem()
                    .updateTime(resource.getUid(), PathUtils.getParentPath(resource.getPath()), List.of(resource.getName()), attribute);
        } catch (IOException e) {
            throw new PropertySetException(Response.Status.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @Override
    public PropertyMetaData getPropertyMetaData(QName name, WebDavItem resource) throws NotAuthorizedException, BadRequestException {
        if (resource == null) {
            return null;
        }
        return new PropertyMetaData(PropertyAccessibility.WRITABLE, Date.class);
    }

    @Override
    public List<QName> getAllPropertyNames(WebDavItem resource) throws NotAuthorizedException, BadRequestException {
        if (resource == null) {
            return List.of();
        }
        return List.of(
                new QName("urn:schemas-microsoft-com:", CREATION_TIME),
                new QName("urn:schemas-microsoft-com:", LAST_MODIFIED_TIME)
        );
    }

    @Override
    public boolean isMatch(QName name) {
        return name.getLocalPart().equals(CREATION_TIME) || name.getLocalPart().equals(LAST_MODIFIED_TIME);
    }
}
