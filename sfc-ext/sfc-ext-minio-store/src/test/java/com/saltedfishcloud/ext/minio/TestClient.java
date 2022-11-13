package com.saltedfishcloud.ext.minio;

import com.saltedfishcloud.ext.minio.utils.MinioUtils;
import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.Item;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.ByteArrayInputStream;

public class TestClient {
    public MinioProperties getProperties() {
        MinioProperties properties = new MinioProperties();
        properties.setBucket("xyy-test");
        properties.setAccessKey("test");
        properties.setSecretKey("test123456");
        return properties;
    }
    public MinioClient getClient() {
        MinioProperties properties = getProperties();
        return MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
    }

    @Test
    public void testMakeBucket() throws Exception {
        MinioClient client = getClient();
        MinioProperties properties = getProperties();
        if (!client.bucketExists(BucketExistsArgs.builder().bucket(properties.getBucket()).build())) {
            client.makeBucket(MakeBucketArgs.builder().bucket(properties.getBucket()).build());
            System.out.println("创建桶：xyt-test");
        } else {
            System.out.println("桶xyy-test已存在");
        }
        System.out.println("测试桶创建：完成");
    }

    @Test
    public void testExist() throws Exception {
        MinioClient client = getClient();
        MinioProperties properties = getProperties();
        StatObjectResponse object;

        try {
            object = client.statObject(
                    StatObjectArgs.builder()
                            .bucket(properties.getBucket())
                            .object("hello.txt")
                            .build()
            );
            System.out.println(object.object() + "存在，大小为：" + object.size());
        } catch (ErrorResponseException e) {
            if(e.response().code() == 404) {
                System.out.println("hello.txt2不存在");
            }
        }

        try {
            object = client.statObject(
                    StatObjectArgs.builder()
                            .bucket(properties.getBucket())
                            .object("hello.txt2")
                            .build()
            );
            System.out.println(object.object() + "存在，大小为：" + object.size());
        } catch (ErrorResponseException e) {
            if(e.response().code() == 404) {
                System.out.println("hello.txt2不存在");
            }
        }


        try {
            object = client.statObject(
                    StatObjectArgs.builder()
                            .bucket(properties.getBucket())
                            .object(MinioUtils.toDirectoryName("dir/dir2"))
                            .build()
            );
            System.out.println(object.object() + "存在，大小为：" + object.size() + " 是文件夹");
        } catch (ErrorResponseException e) {
            if(e.response().code() == 404) {
                System.out.println("dir/dir2不存在");
            }
        }

    }

    @Test
    public void testUpload() throws Exception {
        MinioClient client = getClient();
        MinioProperties properties = getProperties();
        testMakeBucket();
        ClassPathResource resource = new ClassPathResource("1.txt");
        client.putObject(PutObjectArgs.builder()
                        .bucket(properties.getBucket())
                        .object("hello.txt")
                        .stream(resource.getInputStream(), resource.contentLength(), 1024 * 1024 * 20)
                        .build());
        System.out.println("文件hello.txt上传成功");


        client.putObject(PutObjectArgs.builder()
                .bucket(properties.getBucket())
                .object(MinioUtils.toDirectoryName("dir"))
                .stream(new ByteArrayInputStream(new byte[0] , 0, 0), 0, 1024 * 1024 * 20)
                .build());

        client.putObject(PutObjectArgs.builder()
                .bucket(properties.getBucket())
                .object(MinioUtils.toDirectoryName("dir/dir2"))
                .stream(new ByteArrayInputStream(new byte[0] , 0, 0), 0, 1024 * 1024 * 20)
                .build());
        System.out.println("文件夹dir/dir2创建成功");


        client.putObject(PutObjectArgs.builder()
                .bucket(properties.getBucket())
                .object("dir222/dir2/test.txt")
                .stream(resource.getInputStream(), resource.contentLength(), 1024 * 1024 * 20)
                .build());
        System.out.println("文件dir222/dir2/test.txt上传成功");
    }

    @Test
    public void testListFile() throws Exception {
        MinioClient client = getClient();
        MinioProperties properties = getProperties();
        testMakeBucket();
        testUpload();
        testExist();
        System.out.println("=====获取根目录文件列表=====");
        Iterable<Result<Item>> results = client.listObjects(ListObjectsArgs.builder().bucket(properties.getBucket()).build());
        for (Result<Item> result : results) {
            Item item = result.get();
            System.out.println("文件名：" + item.objectName() + " 大小：" + item.size() + " 是否为文件夹：" + item.isDir());
        }
        System.out.println("=====获取dir222/文件列表=====");
        results = client.listObjects(ListObjectsArgs.builder().bucket(properties.getBucket()).prefix("dir222/").build());
        for (Result<Item> result : results) {
            Item item = result.get();
            System.out.println("文件名：" + item.objectName() + " 大小：" + item.size() + " 是否为文件夹：" + item.isDir());
        }


        System.out.println("=====获取dir/dir2文件列表=====");
        results = client.listObjects(ListObjectsArgs.builder().bucket(properties.getBucket()).prefix("dir/dir2").build());
        for (Result<Item> result : results) {
            Item item = result.get();
            System.out.println("文件名：" + item.objectName() + " 大小：" + item.size() + " 是否为文件夹：" + item.isDir());
        }

        System.out.println("====创建文件夹dir/dir21====");
        MinioUtils.mkdir(client, properties.getBucket(), "dir/dir21");

        System.out.println("=====获取dir/dir2文件列表=====");
        results = client.listObjects(ListObjectsArgs.builder().bucket(properties.getBucket()).prefix("dir/dir2").build());
        for (Result<Item> result : results) {
            Item item = result.get();
            System.out.println("文件名：" + item.objectName() + " 大小：" + item.size() + " 是否为文件夹：" + item.isDir());
        }

        System.out.println("=====获取dir/dir2/文件列表=====");
        results = client.listObjects(ListObjectsArgs.builder().bucket(properties.getBucket()).prefix("dir/dir2/").build());
        for (Result<Item> result : results) {
            Item item = result.get();
            System.out.println("文件名：" + item.objectName() + " 大小：" + item.size() + " 是否为文件夹：" + item.isDir());
        }



        System.out.println("=====获取newDir/文件列表=====");
        results = client.listObjects(ListObjectsArgs.builder().bucket(properties.getBucket()).prefix("newDir/").build());
        for (Result<Item> result : results) {
            Item item = result.get();
            System.out.println("文件名：" + item.objectName() + " 大小：" + item.size() + " 是否为文件夹：" + item.isDir());
        }

    }

    @Test
    public void testMkdir() throws Exception {
        MinioClient client = getClient();
        MinioProperties properties = getProperties();
        MinioUtils.mkdir(client, properties.getBucket(), "/newDir");
    }

    @Test
    public void testCopy() throws Exception {
        MinioClient client = getClient();
        MinioProperties properties = getProperties();
        testMakeBucket();
        testMkdir();
        testUpload();
        System.out.println("复制hello.txt到hello2.txt");
        client.copyObject(
                CopyObjectArgs.builder()
                        .bucket(properties.getBucket())
                        .source(CopySource.builder().bucket(properties.getBucket()).object("hello.txt").build())
                        .object("hello2.txt")
                        .taggingDirective(Directive.REPLACE)
                        .build()
        );
    }
}
