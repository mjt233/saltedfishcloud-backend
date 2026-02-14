package com.sfc.ext.webdav.core.propsource;

import com.sfc.ext.webdav.enums.ResourceArea;
import com.sfc.ext.webdav.model.resource.WebDavItem;
import com.xiaotao.saltedfishcloud.model.FileSystemStatus;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.utils.SpringContextUtils;
import io.milton.http.exceptions.BadRequestException;
import io.milton.http.exceptions.NotAuthorizedException;
import org.springframework.data.util.Lazy;

import javax.xml.namespace.QName;
import java.util.List;
import java.util.Optional;

import static com.xiaotao.saltedfishcloud.model.FileSystemStatus.AREA_PRIVATE;
import static com.xiaotao.saltedfishcloud.model.FileSystemStatus.AREA_PUBLIC;

public class QuotaPropertySource extends AbstractSfcPropertySource {
    private static final String QUOTA_AVAILABLE_BYTES = "quota-available-bytes";
    private static final String QUOTA_USED_BYTES = "quota-used-bytes";
    private final Lazy<DiskFileSystemManager> diskFileSystemManagerLazy = Lazy.of(() -> SpringContextUtils.getContext().getBean(DiskFileSystemManager.class));

    @Override
    public boolean isMatch(QName name) {
        return name.getLocalPart().equals(QUOTA_AVAILABLE_BYTES) || name.getLocalPart().equals(QUOTA_USED_BYTES);
    }

    @Override
    public Object getProperty(QName name, WebDavItem resource) throws NotAuthorizedException {
        return getFileSystemStatus(name, resource == null ? ResourceArea.PRIVATE : resource.getResourceArea());
    }

    @Override
    public void setProperty(QName name, Object value, WebDavItem resource) throws PropertySetException, NotAuthorizedException {

    }

    @Override
    public PropertyMetaData getPropertyMetaData(QName name, WebDavItem resource) throws NotAuthorizedException, BadRequestException {
        return new PropertyMetaData(PropertyAccessibility.READ_ONLY, Long.class);
    }

    @Override
    public List<QName> getAllPropertyNames(WebDavItem resource) throws NotAuthorizedException, BadRequestException {
        return List.of(
                new QName("DAV:", QUOTA_AVAILABLE_BYTES),
                new QName("DAV:", QUOTA_USED_BYTES)
        );
    }

    private Object getFileSystemStatus(QName name, ResourceArea resourceArea) {
        String area = resourceArea == ResourceArea.PRIVATE ? AREA_PRIVATE : AREA_PUBLIC;

        Optional<FileSystemStatus> statusOptional = diskFileSystemManagerLazy.get().getMainFileSystem().getStatus()
                .stream()
                .filter(status -> area.equals(status.getArea()))
                .findAny();

        if (name.getLocalPart().equals(QUOTA_AVAILABLE_BYTES)) {
            // 返回剩余空间
            return statusOptional
                    .map(FileSystemStatus::getFree)
                    .orElse(null);
        }

        if (name.getLocalPart().equals(QUOTA_USED_BYTES)) {
            // 返回已用空间
            return statusOptional
                    .map(FileSystemStatus::getUsed)
                    .orElse(null);
        }
        return null;
    }

}
