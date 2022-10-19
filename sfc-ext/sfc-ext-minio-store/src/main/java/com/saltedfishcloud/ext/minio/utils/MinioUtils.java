package com.saltedfishcloud.ext.minio.utils;

import com.saltedfishcloud.ext.minio.MinioResource;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import io.minio.*;
import io.minio.errors.*;
import io.minio.messages.ErrorResponse;
import io.minio.messages.Item;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.temporal.ChronoField;
import java.util.Optional;

public class MinioUtils {

    /**
     * 将文件名表示为目录文件名
     * @param name  文件名
     * @return      表示目录的文件名
     */
    public static String toDirectoryName(String name) {
        String path = toMinioObjectName(name);

        if (path.endsWith("/")) {
            return path;
        } else {
            return path + "/";
        }
    }


    public static StatObjectResponse getStat(MinioClient client, String bucket, String object) throws IOException {
        try {
            return client.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(MinioUtils.toMinioObjectName(object))
                    .build()
            );
        }catch (Exception e) {
            if (isNotFound(e)) {
                // 文件不存在，则去测试目录
                if (!object.endsWith("/")) {
                    return getStat(client, bucket, object + "/");
                } else {
                    return null;
                }
            } else {
                throw toIOException(e);
            }
        }
    }

    /**
     * 将路径转为minio的对象名称（移除前面的/）
     */
    public static String toMinioObjectName(String path) {
        if (path.startsWith("/")) {
            return path.substring(1);
        } else {
            return path;
        }
    }

    public static boolean isDir(StatObjectResponse stat) {
        return stat != null && stat.object().endsWith("/") && stat.size() == 0;
    }

    /**
     * 在minio上创建文件夹
     * @param client
     * @param bucket
     * @param path
     */
    public static ObjectWriteResponse mkdir(MinioClient client, String bucket, String path) throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        return client.putObject(PutObjectArgs.builder()
                .bucket(bucket)
                .object(MinioUtils.toDirectoryName(path))
                .stream(new ByteArrayInputStream(new byte[0], 0, 0), 0, 1024 * 1024 * 5)
                .build());
    }

    public static IOException toIOException(Exception e) {
        if (e instanceof IOException) {
            return (IOException)e;
        } else {
            return new IOException(e);
        }
    }

    public static boolean isRootPath(String path) {
        return !StringUtils.hasText(path) || "/".equals(path);
    }

    public static boolean isNotFound(Exception e) {
        if (e instanceof ErrorResponseException) {
            return ((ErrorResponseException) e).response().code() == 404;
        } else {
            return false;
        }
    }

    public static boolean isInvalidAccessKeyId(Exception e) {
        if (e instanceof ErrorResponseException) {
            return ((ErrorResponseException) e).response().code() == 403;
        } else {
            return false;
        }
    }

    public static FileInfo itemToFileInfo(MinioClient client, String bucket, Item item) {
        boolean isDir = item.objectName().endsWith("/");
        long lastModified;
        try {
            lastModified = item.lastModified().getLong(ChronoField.MILLI_OF_SECOND);
        } catch (NullPointerException e) {
            lastModified = 0;
        }
        return new FileInfo(
                StringUtils.getURLLastName(item.objectName()),
                item.size(),
                isDir ? FileInfo.TYPE_DIR : FileInfo.TYPE_FILE,
                MinioUtils.toDirectoryName(item.objectName()),
                lastModified,
                isDir ? null : () -> {
                    try {
                        return client.getObject(GetObjectArgs.builder()
                                        .bucket(bucket)
                                        .object(item.objectName())
                                .build());
                    } catch (Exception e) {
                        throw  toIOException(e);
                    }
                }
            );
    }

    /**
     * 获取文件对象资源。
     * 当资源不存在时，返回null
     */
    public static MinioResource getObjectResource(MinioClient client, String bucket, String object) throws ServerException, InsufficientDataException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        try {
            StatObjectResponse status = client.statObject(StatObjectArgs.builder().bucket(bucket).object(object).build());
            return MinioResource.builder()
                    .statObjectResponse(status)
                    .getResponseFunc(() -> {
                        try {
                            return client.getObject(GetObjectArgs.builder().bucket(bucket).object(object).build());
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .build();
        } catch (ErrorResponseException e) {
            if (e.response().code() == 404) {
                return null;
            } else {
                throw new IOException(e);
            }
        }
    }
}
