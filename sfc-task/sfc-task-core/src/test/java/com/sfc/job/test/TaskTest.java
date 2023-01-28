package com.sfc.job.test;

import com.sfc.job.*;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.gson.GsonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.io.IOException;

@SpringBootTest
@RunWith(SpringRunner.class)
@SpringBootApplication(
        exclude= {DataSourceAutoConfiguration.class, GsonAutoConfiguration.class},
        scanBasePackages = {
                "com.sfc.job"
        }
)
@EnableTransactionManagement
@MapperScan("com.sfc.job")
@EnableScheduling
@EnableCaching
@EnableJpaAuditing
@EnableConfigurationProperties
@Slf4j
@EntityScan("com.sfc.job")
@EnableJpaRepositories(basePackages = "com.sfc.job")
@ActiveProfiles("test")
public class TaskTest {
    @Autowired
    private RPCManager rpcManager;

    @Test
    public void testMethod() throws IOException {
        rpcManager.registerRpcHandler("testFunc", request -> RPCResponse.<String>builder()
                .isHandled(true)
                .result("Hello World，你的RPC请求id是：" + request.getRequestId() + " 请求参数是：" + request.getParam())
                .build());

        RPCRequest request = RPCRequest.builder()
                .functionName("testFunc")
                .param("你好")
                .build();
        RPCResponse<String> result = rpcManager.call(request, String.class);
        log.info("处理结果：{}", result);
    }
}
