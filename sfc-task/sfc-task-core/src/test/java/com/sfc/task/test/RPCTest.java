package com.sfc.task.test;

import com.sfc.task.rpc.RPCManager;
import com.sfc.task.rpc.RPCRequest;
import com.sfc.task.rpc.RPCResponse;
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
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.io.IOException;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest
@RunWith(SpringRunner.class)
@SpringBootApplication(
        exclude= {DataSourceAutoConfiguration.class, GsonAutoConfiguration.class},
        scanBasePackages = {
                "com.sfc.task"
        }
)
@EnableTransactionManagement
@MapperScan("com.sfc.job.dao")
@EnableScheduling
@EnableCaching
@EnableJpaAuditing
@EnableConfigurationProperties
@Slf4j
@EntityScan("com.sfc.task")
@EnableJpaRepositories(basePackages = "com.sfc.task")
@ActiveProfiles("test")
public class RPCTest {
    @Autowired
    private RPCManager rpcManager;

    @Autowired
    private RedisConnectionFactory factory;

    /**
     * 测试多个管理器监听时
     * @throws IOException
     */
    @Test
    public void testHandleOnce() throws IOException, BrokenBarrierException, InterruptedException {
        AtomicInteger aCount = new AtomicInteger();
        AtomicInteger bCount = new AtomicInteger();
        RPCManager rpcManager2 = new RPCManager(factory);
        this.rpcManager.registerRpcHandler("testFunc", request -> {
            boolean isHandled = Integer.parseInt(request.getParam()) % 2 == 0;
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (isHandled) {
                aCount.incrementAndGet();
            }
            return RPCResponse.<String>builder()
                    .isHandled(isHandled)
                    .result("处理者:1号 RPC请求id是：" + request.getRequestId() + " 请求参数是：" + request.getParam())
                    .build();
        });

        rpcManager2.registerRpcHandler("testFunc", request -> {
            boolean isHandled = Integer.parseInt(request.getParam()) % 2 != 0;
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (isHandled) {
                bCount.incrementAndGet();
            }
            return RPCResponse.<String>builder()
                    .isHandled(isHandled)
                    .result("处理者:2号 RPC请求id是：" + request.getRequestId() + " 请求参数是：" + request.getParam())
                    .build();
        });


        CyclicBarrier barrier = new CyclicBarrier(11);
        for (int i = 0; i < 10; i++) {
            int j = i;
            new Thread(() -> {
                try {
                    RPCRequest request;
                    RPCResponse<String> response;
                    request = RPCRequest.builder()
                            .functionName("testFunc")
                            .param(j + "")
                            .build();
                    response = this.rpcManager.call(request, String.class);
                    System.out.println("处理结果：" + response.getResult());
                    barrier.await();
                } catch (IOException | InterruptedException | BrokenBarrierException e) {
                    e.printStackTrace();
                }
            }).start();
        }
        barrier.await();
        System.out.println("a处理次数:" + aCount + " b处理次数:" + bCount);
    }
}
