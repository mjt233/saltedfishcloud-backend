package com.sfc.rpc;

import com.sfc.ext.localmq.config.LocalMQProperties;
import com.sfc.ext.localmq.core.LocalMQService;
import com.sfc.rpc.exception.RPCIgnoreException;
import com.xiaotao.saltedfishcloud.model.ClusterNodeInfo;
import com.xiaotao.saltedfishcloud.model.RequestParam;
import com.xiaotao.saltedfishcloud.service.ClusterService;
import com.xiaotao.saltedfishcloud.service.mq.MQService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MQRPCInvoker} 的单元测试。
 */
class MQRPCInvokerTest {
    /**
     * 测试 RPC 请求会被匹配节点处理并返回正确响应。
     *
     * @throws IOException RPC 调用失败时抛出
     */
    @Test
    @DisplayName("基于MQService的RPC调用会命中匹配处理器")
    void callShouldReturnHandledResponseFromMatchingHandler() throws IOException {
        try (LocalMQService mqService = createLocalMqService()) {
            ClusterService clusterService = createClusterService(2);
            RPCManager pair1 = createRpcManager(mqService, clusterService);
            RPCManager pair2 = createRpcManager(mqService, clusterService);

            pair1.getRegistry().registerRpcHandler("testFunc", request -> {
                boolean handled = Integer.parseInt(request.getParam()) % 2 == 0;
                return RPCResponse.<String>builder()
                        .isHandled(handled)
                        .result("handler-1:" + request.getParam())
                        .build();
            });
            pair2.getRegistry().registerRpcHandler("testFunc", request -> {
                boolean handled = Integer.parseInt(request.getParam()) % 2 != 0;
                return RPCResponse.<String>builder()
                        .isHandled(handled)
                        .result("handler-2:" + request.getParam())
                        .build();
            });

            RPCResponse<String> evenResponse = pair1.getInvoker().call(RPCRequest.builder()
                    .functionName("testFunc")
                    .param("2")
                    .build(), String.class, Duration.ofSeconds(2));
            RPCResponse<String> oddResponse = pair1.getInvoker().call(RPCRequest.builder()
                    .functionName("testFunc")
                    .param("3")
                    .build(), String.class, Duration.ofSeconds(2));

            assertNotNull(evenResponse);
            assertNotNull(oddResponse);
            assertEquals("handler-1:2", evenResponse.getResult());
            assertEquals("handler-2:3", oddResponse.getResult());
            assertEquals(Boolean.TRUE, evenResponse.getIsHandled());
            assertEquals(Boolean.TRUE, oddResponse.getIsHandled());
        } catch (Exception e) {
            throw new IOException("测试执行失败", e);
        }
    }

    /**
     * 测试 callAll 能够收集所有节点返回的响应，包括忽略响应。
     *
     * @throws IOException RPC 调用失败时抛出
     */
    @Test
    @DisplayName("callAll会收集所有节点的响应")
    void callAllShouldCollectAllResponses() throws IOException {
        try (LocalMQService mqService = createLocalMqService()) {
            ClusterService clusterService = createClusterService(2);
            RPCManager pair1 = createRpcManager(mqService, clusterService);
            RPCManager pair2 = createRpcManager(mqService, clusterService);

            pair1.getRegistry().registerRpcHandler("testFunc", ignored -> RPCResponse.success("handler-1"));
            pair2.getRegistry().registerRpcHandler("testFunc", ignored -> RPCResponse.ignore());

            List<RPCResponse<String>> responses = pair1.getInvoker().callAll(RPCRequest.builder()
                    .functionName("testFunc")
                    .isReportIgnore(true)
                    .param("payload")
                    .build(), String.class, Duration.ofSeconds(2));

            assertEquals(2, responses.size());
            assertEquals(1, responses.stream().filter(response -> Boolean.TRUE.equals(response.getIsHandled())).count());
            assertEquals(1, responses.stream().filter(response -> !Boolean.TRUE.equals(response.getIsHandled())).count());
            assertTrue(responses.stream().anyMatch(response -> "handler-1".equals(response.getResult())));
        } catch (Exception e) {
            throw new IOException("测试执行失败", e);
        }
    }

    /**
     * 测试当所有节点都忽略请求且请求要求报告忽略结果时，会抛出 {@link RPCIgnoreException}。
     */
    @Test
    @DisplayName("所有节点忽略时抛出RPCIgnoreException")
    void callShouldThrowIgnoreExceptionWhenAllNodesIgnore() {
        try (LocalMQService mqService = createLocalMqService()) {
            ClusterService clusterService = createClusterService(2);
            RPCManager pair1 = createRpcManager(mqService, clusterService);
            RPCManager pair2 = createRpcManager(mqService, clusterService);

            pair1.getRegistry().registerRpcHandler("testFunc", ignored -> RPCResponse.ignore());
            pair2.getRegistry().registerRpcHandler("testFunc", ignored -> RPCResponse.ignore());

            assertThrows(RPCIgnoreException.class, () -> pair1.getInvoker().call(RPCRequest.builder()
                    .functionName("testFunc")
                    .isReportIgnore(true)
                    .param("payload")
                    .build(), String.class, Duration.ofSeconds(2)));
        } catch (Exception e) {
            throw new IllegalStateException("测试执行失败", e);
        }
    }

    /**
     * 通过反射创建测试使用的 {@code LocalMQService} 实例。
     * <p>
     * 这样可以避免 IDE 在未刷新 Maven 模型时对测试源码产生误报，同时运行时仍然使用
     * {@code sfc-ext-local-mq} 提供的真实实现。
     *
     * @return 本地 MQ 测试包装对象
     */
    private LocalMQService createLocalMqService() {
        return new LocalMQService(new LocalMQProperties());
    }


    private RPCManager createRpcManager(MQService mqService, ClusterService clusterService) {
        RPCRegistryStore rpcRegistryStore = new RPCRegistryStore();
        MQRPCInvoker rpcInvoker = new MQRPCInvoker(mqService, clusterService, rpcRegistryStore);
        DefaultRPCRegistry rpcRegistry = new DefaultRPCRegistry(rpcInvoker, rpcRegistryStore);
        return new RPCManager() {
            @Override
            public RPCInvoker getInvoker() {
                return rpcInvoker;
            }

            @Override
            public RPCRegistry getRegistry() {
                return rpcRegistry;
            }
        };
    }

    /**
     * 创建一个固定节点数量的测试集群服务。
     *
     * @param nodeCount 节点数量
     * @return 测试集群服务
     */
    private ClusterService createClusterService(int nodeCount) {
        List<ClusterNodeInfo> nodes = new ArrayList<>();
        for (int i = 0; i < nodeCount; i++) {
            nodes.add(ClusterNodeInfo.builder()
                    .id((long) i + 1)
                    .host("node-" + (i + 1))
                    .ip("127.0.0." + (i + 1))
                    .cpu(1)
                    .httpPort(8000 + i)
                    .build());
        }
        return new ClusterService() {
            /**
             * {@inheritDoc}
             */
            @Override
            public List<ClusterNodeInfo> listNodes() {
                return nodes;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public int getNodeCount() {
                return nodeCount;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public ClusterNodeInfo getSelf() {
                return nodes.getFirst();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public ClusterNodeInfo getNodeById(Long id) {
                return nodes.stream().filter(node -> node.getId().equals(id)).findFirst().orElse(null);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public <T> ResponseEntity<T> request(Long nodeId, RequestParam param, ParameterizedTypeReference<T> typeReference) {
                throw new UnsupportedOperationException("测试场景不需要HTTP RPC");
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void registerSelf() {
                throw new UnsupportedOperationException("测试场景不需要注册自身");
            }
        };
    }

    /**
     * 测试使用的 RPC 组件组合。
     *
     * @param rpcInvoker  RPC 调用器
     * @param rpcRegistry RPC 注册中心
     */
    private record RPCPair(MQRPCInvoker rpcInvoker, DefaultRPCRegistry rpcRegistry) {
    }
}



