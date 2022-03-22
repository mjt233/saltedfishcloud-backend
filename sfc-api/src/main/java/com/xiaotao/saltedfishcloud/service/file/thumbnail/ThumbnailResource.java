package com.xiaotao.saltedfishcloud.service.file.thumbnail;

import org.springframework.core.io.Resource;
import org.springframework.lang.NonNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;

public abstract class ThumbnailResource implements Resource {
    /**
     * 获取资源的MIME描述类型
     * @return  资源的MIME描述类型
     */
    abstract String getContentType();

    public static ThumbnailResource fromResource(Resource resource, String contentType) {
        return new ThumbnailResource() {

            @Override
            public String getContentType() {
                return contentType;
            }

            @Override
            public boolean exists() {
                return resource.exists();
            }

            @Override
            @NonNull
            public URL getURL() throws IOException {
                return resource.getURL();
            }

            @Override
            @NonNull
            public URI getURI() throws IOException {
                return resource.getURI();
            }

            @Override
            @NonNull
            public File getFile() throws IOException {
                return resource.getFile();
            }

            @Override
            public long contentLength() throws IOException {
                return resource.contentLength();
            }

            @Override
            public long lastModified() throws IOException {
                return resource.lastModified();
            }

            @Override
            @NonNull
            public Resource createRelative(@NonNull String relativePath) throws IOException {
                return resource.createRelative(relativePath);
            }

            @Override
            public String getFilename() {
                return resource.getFilename();
            }

            @Override
            @NonNull
            public String getDescription() {
                return resource.getDescription();
            }

            @Override
            @NonNull
            public InputStream getInputStream() throws IOException {
                return resource.getInputStream();
            }
        };
    }
}
