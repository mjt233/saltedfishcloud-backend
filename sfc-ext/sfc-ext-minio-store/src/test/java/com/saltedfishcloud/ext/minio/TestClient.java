package com.saltedfishcloud.ext.minio;

import io.minio.MinioClient;
import org.junit.jupiter.api.Test;

public class TestClient {
    @Test
    public void testMakeBucket() throws Exception {
        MinioClient client = new MinioClient("http://127.0.0.1:9000", "test", "test123456");
        if(!client.bucketExists("test-bucket")) {
            client.makeBucket("test-bucket");
        }
        System.out.println("测试完成");
    }
}
