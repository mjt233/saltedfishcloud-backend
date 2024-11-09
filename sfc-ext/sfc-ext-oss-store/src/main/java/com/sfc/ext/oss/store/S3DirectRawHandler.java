package com.sfc.ext.oss.store;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.*;
import com.sfc.ext.oss.OSSProperty;
import com.sfc.ext.oss.util.OSSPathUtils;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.store.DirectRawStoreHandler;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.ResourceUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import com.xiaotao.saltedfishcloud.utils.identifier.IdUtil;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;
import org.springframework.data.util.Lazy;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@Slf4j
public class S3DirectRawHandler implements DirectRawStoreHandler {
    private final static String LOG_PREFIX = "[OSS存储]";
    private final AmazonS3 s3Client;
    private final OSSProperty property;

    public S3DirectRawHandler(OSSProperty ossProperty) {
        this.property = ossProperty;
        this.s3Client = AmazonS3ClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(ossProperty.getAccessKey(), ossProperty.getSecretKey())))
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(
                        ossProperty.getServiceEndPoint(),
                        ""))
                .withPathStyleAccessEnabled(false)
                .withChunkedEncodingDisabled(true)
                .build();
    }

    @Override
    public boolean exist(String path) throws IOException {
        if (path == null) {
            return false;
        }
        if ("/".equals(path) || path.isBlank()) {
            return true;
        }
        return getFileInfo(path) != null;
    }

    @Override
    public boolean isEmptyDirectory(String path) throws IOException {
        return listFiles(path).size() == 0;
    }

    @Override
    public Resource getResource(String path) throws IOException {
        FileInfo fileInfo = getFileInfo(path);
        if (fileInfo == null || fileInfo.isDir()) {
            return null;
        }
        Resource fileResource = ResourceUtils.bindFileInfo(new AbstractResource() {
            @NotNull
            @Override
            public String getDescription() {
                return "oss [" + path + "]";
            }

            @NotNull
            @Override
            public InputStream getInputStream() throws IOException {
                return fileInfo.getStreamSource().getInputStream();
            }
        }, fileInfo);
        if (Boolean.TRUE.equals(property.getUseUrlRedirect())) {
            return ResourceUtils.bindRedirectUrl(fileResource, () -> getObjectUrlSupplier(OSSPathUtils.toOSSObjectName(path)).get().toString());
        } else {
            return fileResource;
        }
    }

    @Override
    public List<FileInfo> listFiles(String path) throws IOException {
        ListObjectsV2Request request = new ListObjectsV2Request();
        request.setBucketName(property.getBucket());
        boolean isRoot = OSSPathUtils.isRootPath(path);
        String ossPath = isRoot ? "" : OSSPathUtils.toDirectoryName(path);
        request.setPrefix(ossPath);
        request.setDelimiter("/");
        ListObjectsV2Result v2Result = s3Client.listObjectsV2(request);
        Stream<FileInfo> fileStream = v2Result.getObjectSummaries()
                .stream()
                .filter(obj -> !obj.getKey().equals(ossPath) )
                .map(obj -> {
                    String fileName = obj.getKey().substring(ossPath.length());
                    FileInfo fileInfo = new FileInfo();
                    fileInfo.setName(fileName);
                    fileInfo.setSize(obj.getSize());
                    fileInfo.setMtime(obj.getLastModified().getTime());
                    fileInfo.setIsMount(true);
                    fileInfo.setMd5(obj.getETag().toLowerCase());
                    return fileInfo;
                });
        Stream<FileInfo> dirStream = Optional.ofNullable(v2Result.getCommonPrefixes()).orElse(Collections.emptyList())
                .stream()
                .map(dir -> {
                    FileInfo fileInfo = new FileInfo();
                    fileInfo.setName(PathUtils.getLastNode(dir.substring(0, dir.length() - 1)));
                    fileInfo.setSize(-1L);
                    fileInfo.setType(FileInfo.TYPE_DIR);
                    return fileInfo;
                });
        return Stream.concat(fileStream, dirStream).collect(Collectors.toList());
    }

    @Override
    public FileInfo getFileInfo(String path) throws IOException {
        S3Object object;
        String ossPath = OSSPathUtils.toOSSObjectName(path);
        if ("".equals(ossPath)) {
            return FileInfo.builder()
                    .type(FileInfo.TYPE_DIR)
                    .size(-1L)
                    .name("")
                    .isMount(true)
                    .build();
        }
        try {
            object = s3Client.getObject(property.getBucket(), ossPath);
        } catch (AmazonS3Exception e) {
            // 文件无法获取到时，可能是目录，在末尾加上/再试一次
            if (e.getStatusCode() == 404) {
                try {
                    ossPath += "/";
                    object = s3Client.getObject(property.getBucket(), ossPath);
                } catch (AmazonS3Exception e2) {
                    if (e2.getStatusCode() == 404) {
                        return null;
                    } else {
                        throw new IOException(e);
                    }
                }
            } else {
                throw new IOException(e);
            }
        }

        ObjectMetadata objectMetadata = object.getObjectMetadata();
        boolean isDir = OSSPathUtils.isDir(ossPath);
        FileInfo fileInfo = new FileInfo();
        String objectKey = object.getKey();
        fileInfo.setName(StringUtils.getURLLastName(objectKey));
        fileInfo.setSize(isDir ? -1L : objectMetadata.getContentLength());
        fileInfo.setType(isDir ? FileInfo.TYPE_DIR : FileInfo.TYPE_FILE);
        fileInfo.setMd5(isDir ? null : objectMetadata.getETag().toLowerCase());
        fileInfo.setMtime(objectMetadata.getLastModified().getTime());
        fileInfo.setIsMount(true);

        if (!isDir) {
            fileInfo.setStreamSource(() -> getObjectUrlSupplier(objectKey).get().openStream());
        }
        return fileInfo;
    }

    /**
     * 获取存储对象的下载URL
     * @param objKey    存储对象Key
     */
    private URL getObjectUrl(String objKey) {
        return getObjectUrlSupplier(objKey).get();
    }

    /**
     * 获取存储对象的下载URL
     * @param objKey    存储对象Key
     */
    private Supplier<URL> getObjectUrlSupplier(String objKey) {
        return Lazy.of(() -> {
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.DATE, Integer.parseInt(Optional.ofNullable(property.getUrlExpire()).orElse("1")));
            URL url = s3Client.generatePresignedUrl(property.getBucket(), objKey, calendar.getTime());
            if (property.getCdnDomain() == null || property.getCdnDomain().isBlank()) {
                return url;
            }
            try {
                return new URL(property.getCdnDomain() + url.getFile());
            } catch (MalformedURLException e) {
                throw new RuntimeException("OSS CDN解析失败: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public boolean delete(String path) throws IOException {
        FileInfo fileInfo = getFileInfo(path);
        boolean selfIsObj = true;
        if (fileInfo == null) {
            List<FileInfo> fileInfos = listFiles(path);
            if (!fileInfos.isEmpty()) {
                fileInfo = new FileInfo();
                fileInfo.setName(StringUtils.getURLLastName(path));
                fileInfo.setType(FileInfo.TYPE_DIR);
                fileInfo.setSize(-1L);
                selfIsObj = false;
            } else {
                return false;
            }
        }
        if (fileInfo.isFile()) {
            s3Client.deleteObject(new DeleteObjectRequest(property.getBucket(), OSSPathUtils.toOSSObjectName(path)));
        } else {
            ListObjectsV2Request request = new ListObjectsV2Request();
            request.setBucketName(property.getBucket());
            boolean isRoot = OSSPathUtils.isRootPath(path);
            String ossPath = OSSPathUtils.toDirectoryName(path);
            if (!isRoot) {
                request.setPrefix(ossPath);
            }

            // 列出目录下的文件，删除
            ListObjectsV2Result listObjectsV2Result = s3Client.listObjectsV2(property.getBucket(), ossPath);
            List<DeleteObjectsRequest.KeyVersion> delKeys = Optional.ofNullable(listObjectsV2Result.getObjectSummaries()).orElse(Collections.emptyList())
                    .stream()
                    .map(obj -> new DeleteObjectsRequest.KeyVersion(obj.getKey()))
                    .collect(Collectors.toList());
            DeleteObjectsRequest delRequest = new DeleteObjectsRequest(property.getBucket());

            if (!delKeys.isEmpty()) {
                delRequest.setKeys(delKeys);
                s3Client.deleteObjects(delRequest);
            }

            // 删除子目录
            delKeys = Optional.ofNullable(listObjectsV2Result.getCommonPrefixes()).orElse(Collections.emptyList())
                    .stream()
                    .map(DeleteObjectsRequest.KeyVersion::new)
                    .collect(Collectors.toList());
            if (!delKeys.isEmpty()) {
                delRequest.setKeys(delKeys);
                s3Client.deleteObjects(delRequest);
            }

            // 删除自己
            if (selfIsObj) {
                s3Client.deleteObject(property.getBucket(), ossPath);
            }
        }

        return true;
    }

    @Override
    public boolean mkdir(String path) throws IOException {
        ByteArrayInputStream is = new ByteArrayInputStream(new byte[0]);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(0);
        PutObjectRequest request = new PutObjectRequest(property.getBucket(), OSSPathUtils.toDirectoryName(path), is, metadata);
        s3Client.putObject(request);
        return false;
    }

    @Override
    public long store(FileInfo fileInfo, String path, long size, InputStream inputStream) throws IOException {
        log.debug("{}上传文件到oss, 文件名: {}, 大小: {}, 位置: {}", LOG_PREFIX, fileInfo.getName(), size, path);
        String ossPath = OSSPathUtils.toOSSObjectName(path);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(fileInfo.getSize());
        metadata.setLastModified(new Date(fileInfo.getMtime()));
        PutObjectResult res = s3Client.putObject(property.getBucket(), ossPath, inputStream, metadata);
        return fileInfo.getSize();
    }

    @Override
    public OutputStream newOutputStream(String path) throws IOException {
        Path tmpPath = PathUtils.getTempPath().resolve("oss_upload_tmp_" + IdUtil.getId());
        log.debug("{}为{}创建outputStream, 本地临时文件: {}", LOG_PREFIX, path, tmpPath);
        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(FileOutputStream.class);
        enhancer.setCallback((MethodInterceptor) (obj, method, args, proxy) -> {
            if (method.getName().equals("close")) {
                log.debug("{}{}outputStream开始关闭", LOG_PREFIX, path);
                Object invokeRes = method.invoke(obj, args);
                log.debug("{}{}outputStream关闭完成，开始保存到OSS", LOG_PREFIX, path);
                FileInfo tmpFile = FileInfo.getLocal(tmpPath.toString(), false);
                try (InputStream is = Files.newInputStream(tmpPath)) {
                    store(tmpFile, tmpPath.toString(), tmpFile.getSize(), is);
                }
                log.debug("{}{}通过outputStream保存OSS完成", LOG_PREFIX, path);
                log.debug("{}{}删除本地outputStream临时文件", LOG_PREFIX, tmpPath);
                Files.deleteIfExists(tmpPath);
                return invokeRes;
            } else {
                return method.invoke(obj, args);
            }
        });
        return (OutputStream) enhancer.create(new Class[]{File.class}, new Object[]{tmpPath.toFile()});
    }

    @Override
    public boolean rename(String path, String newName) throws IOException {
        move(path, PathUtils.getParentPath(path) + "/" + newName);
        return true;
    }

    @Override
    public boolean copy(String src, String dest) throws IOException {
        FileInfo srcFile = getFileInfo(src);
        if (srcFile == null) {
            return false;
        }
        String ossSrcPath = srcFile.isFile() ? OSSPathUtils.toOSSObjectName(src) : OSSPathUtils.toDirectoryName(src);
        String ossDestPath = srcFile.isFile() ? OSSPathUtils.toOSSObjectName(dest) : OSSPathUtils.toDirectoryName(dest);
        CopyObjectResult res = s3Client.copyObject(property.getBucket(), ossSrcPath, property.getBucket(), ossDestPath);
        return true;
    }

    @Override
    public boolean move(String src, String dest) throws IOException {
        copy(src, dest);
        delete(src);
        return true;
    }

    @Override
    public boolean mkdirs(String path) throws IOException {
        final FileInfo curPathInfo = getFileInfo(path);
        if (curPathInfo == null) {
            final String parentPath = PathUtils.getParentPath(path);
            if ("/".equals(parentPath) || parentPath.isBlank()) {
                return mkdir(path);
            }
            final FileInfo parentPathInfo = getFileInfo(parentPath);

            if (parentPathInfo == null) {
                mkdirs(parentPath);
                return mkdir(path);
            }

            if (parentPathInfo.isDir()) {
                return mkdir(path);
            } else {
                return false;
            }
        } else {
            return false;
        }
    }
}
