package com.sfc.ext.webdav.store;

import com.github.sardine.DavResource;
import org.springframework.core.io.InputStreamSource;
import org.springframework.core.io.Resource;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;

public class WebDavFileResource implements Resource {
    private final DavResource davResource;
    private final InputStreamSource inputStreamSource;

    public WebDavFileResource(DavResource davResource, InputStreamSource inputStreamSource) {
        this.davResource = davResource;
        this.inputStreamSource = inputStreamSource;
    }

    @Override
    public boolean exists() {
        // 判断资源是否存在，这里假设 davResource 不为空即存在
        return davResource != null;
    }

    @Override
    public URL getURL() throws IOException {
        // 返回资源的 URL，从 davResource 中获取
        return davResource.getHref().toURL();
    }

    @Override
    public URI getURI() throws IOException {
        // 返回资源的 URI，从 davResource 中获取
        return davResource.getHref();
    }

    @Override
    public File getFile() throws IOException {
        // WebDAV 资源通常不直接对应本地文件，因此抛出异常或返回 null
        throw new UnsupportedOperationException("WebDAV resource does not correspond to a local file");
    }

    @Override
    public long contentLength() throws IOException {
        // 返回资源的内容长度，从 davResource 中获取
        return davResource.getContentLength();
    }

    @Override
    public long lastModified() throws IOException {
        // 返回资源的最后修改时间，从 davResource 中获取
        return davResource.getModified().getTime();
    }

    @Override
    public Resource createRelative(String relativePath) throws IOException {
        throw new FileNotFoundException("Cannot create a relative resource for " + getDescription());
    }

    @Override
    public String getFilename() {
        return davResource.getName();
    }

    @Override
    public String getDescription() {
        // 返回资源的描述信息
        return "WebDAV Resource: " + davResource.getHref();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return inputStreamSource.getInputStream();
    }
}
