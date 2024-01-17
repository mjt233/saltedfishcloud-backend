package com.xiaotao.saltedfishcloud.common;

import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.io.Resource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.channels.ReadableByteChannel;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 根据FileInfo的字段信息包装Resource接口
 */
public class FileInfoWrapResource implements Resource {
    private Resource originResource;
    private FileInfo fileInfo;
    private Supplier<FileInfo> fileInfoSupplier;

    public static FileInfoWrapResource create(Resource resource, FileInfo fileInfo) {
        Objects.requireNonNull(fileInfo, "fileInfo 不能为null");
        Objects.requireNonNull(resource, "resource 不能为null");

        FileInfoWrapResource wrapResource = new FileInfoWrapResource();
        wrapResource.originResource = resource;
        wrapResource.fileInfo = fileInfo;
        return wrapResource;
    }

    public static FileInfoWrapResource create(Resource resource, Supplier<FileInfo> fileInfoSupplier) {
        Objects.requireNonNull(fileInfoSupplier, "fileInfoSupplier 不能为null");
        Objects.requireNonNull(resource, "resource 不能为null");

        FileInfoWrapResource wrapResource = new FileInfoWrapResource();
        wrapResource.originResource = resource;
        wrapResource.fileInfoSupplier = fileInfoSupplier;
        return wrapResource;
    }

    public FileInfo getFileInfo() {
        if (this.fileInfo == null) {
            this.fileInfo = fileInfoSupplier.get();
        }
        return this.fileInfo;
    }

    @Override
    public boolean exists() {
        return originResource.exists();
    }

    @NotNull
    @Override
    public URL getURL() throws IOException {
        return originResource.getURL();
    }

    @NotNull
    @Override
    public URI getURI() throws IOException {
        return originResource.getURI();
    }

    @NotNull
    @Override
    public File getFile() throws IOException {
        return originResource.getFile();
    }

    @Override
    public long contentLength() throws IOException {
        return originResource.contentLength();
    }

    @Override
    public long lastModified() throws IOException {
        return getFileInfo().getMtime();
    }

    @NotNull
    @Override
    public Resource createRelative(@NotNull String relativePath) throws IOException {
        return originResource.createRelative(relativePath);
    }

    @Override
    public String getFilename() {
        return getFileInfo().getName();
    }

    @NotNull
    @Override
    public String getDescription() {
        return originResource.getDescription();
    }

    @NotNull
    @Override
    public InputStream getInputStream() throws IOException {
        return originResource.getInputStream();
    }

    @Override
    public boolean isReadable() {
        return originResource.isReadable();
    }

    @Override
    public boolean isOpen() {
        return originResource.isOpen();
    }

    @Override
    public boolean isFile() {
        return originResource.isFile();
    }

    @NotNull
    @Override
    public ReadableByteChannel readableChannel() throws IOException {
        return originResource.readableChannel();
    }
}
