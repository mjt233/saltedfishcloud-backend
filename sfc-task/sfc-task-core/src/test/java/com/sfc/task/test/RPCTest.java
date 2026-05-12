package com.sfc.task.test;

import com.sfc.task.AsyncTaskExecutor;
import com.sfc.rpc.DefaultRPCRegistry;
import com.sfc.rpc.RPCInvoker;
import com.sfc.rpc.RPCRegistry;
import com.sfc.rpc.RPCRequest;
import com.sfc.rpc.RPCResponse;
import com.sfc.rpc.RPCRegistryStore;
import com.sfc.rpc.RedisRPCInvoker;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.gson.GsonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
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
        exclude= {
                DataSourceAutoConfiguration.class,
                GsonAutoConfiguration.class
        },
        scanBasePackages = {
                "com.sfc.task"
        }
)
@EnableTransactionManagement
@EnableScheduling
@EnableCaching
@EnableJpaAuditing
@EnableConfigurationProperties
@Slf4j
@ActiveProfiles("test")
public class RPCTest {
    /**
     * 测试上下文中的RPC注册中心。
     */
    @Autowired
    private RPCRegistry rpcRegistry;

    /**
     * 测试上下文中的RPC调用器。
     */
    @Autowired
    private RPCInvoker rpcInvoker;

    @Autowired
    private RedisConnectionFactory factory;

    @Autowired
    private AsyncTaskExecutor executor;

    /**
     * 测试多个RPC监听器同时存在时仅由匹配节点处理请求。
     *
     * @throws BrokenBarrierException 栅栏等待异常
     * @throws InterruptedException   线程等待中断异常
     */
    @Test
    public void testHandleOnce() throws BrokenBarrierException, InterruptedException {
        AtomicInteger aCount = new AtomicInteger();
        AtomicInteger bCount = new AtomicInteger();
        RPCRegistryStore rpcRegistryStore = new RPCRegistryStore();
        RPCInvoker rpcInvoker2 = new RedisRPCInvoker(factory, null, rpcRegistryStore);
        RPCRegistry rpcRegistry2 = new DefaultRPCRegistry(rpcInvoker2, rpcRegistryStore);
        this.rpcRegistry.registerRpcHandler("testFunc", request -> {
            boolean isHandled = Integer.parseInt(request.getParam()) % 2 == 0;
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("等待测试处理器1执行时线程被中断", e);
            }
            if (isHandled) {
                aCount.incrementAndGet();
            }
            return RPCResponse.<String>builder()
                    .isHandled(isHandled)
                    .result("处理者:1号 RPC请求id是：" + request.getRequestId() + " 请求参数是：" + request.getParam())
                    .build();
        });

        rpcRegistry2.registerRpcHandler("testFunc", request -> {
            boolean isHandled = Integer.parseInt(request.getParam()) % 2 != 0;
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("等待测试处理器2执行时线程被中断", e);
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
                    response = this.rpcInvoker.call(request, String.class);
                    System.out.println("处理结果：" + response.getResult());
                    barrier.await();
                } catch (IOException | InterruptedException | BrokenBarrierException e) {
                    log.error("并发RPC测试执行失败", e);
                }
            }).start();
        }
        barrier.await();
        executor.stop();
        Thread.sleep(5000);
        System.out.println("a处理次数:" + aCount + " b处理次数:" + bCount);
    }
}
