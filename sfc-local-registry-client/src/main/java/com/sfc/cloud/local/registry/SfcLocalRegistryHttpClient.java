package com.sfc.cloud.local.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sfc.cloud.local.registry.config.SfcLocalRegistryProperties;
import com.sfc.cloud.local.registry.model.*;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * local 注册中心 HTTP 客户端。
 * <p>
 * 使用 JDK {@link HttpClient} 与 Jackson，不依赖宿主或 {@code sfc-common} 的运行期类。
 */
public class SfcLocalRegistryHttpClient {
    /** JSON 序列化器 */
    private final ObjectMapper objectMapper;

    /** 客户端配置 */
    private final SfcLocalRegistryProperties properties;

    /** 注册中心 API 根地址 */
    private final String baseUrl;

    /** HTTP 客户端 */
    private final HttpClient httpClient;

    /**
     * 创建 HTTP 客户端。
     *
     * @param properties   客户端配置，必须配置 server-addr
     * @param objectMapper JSON 序列化器，可为 null
     */
    public SfcLocalRegistryHttpClient(SfcLocalRegistryProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.baseUrl = buildBaseUrl(properties);
        Duration connectTimeout = properties.getConnectTimeout() == null
                ? Duration.ofSeconds(3)
                : properties.getConnectTimeout();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    /**
     * 注册实例。
     *
     * @param registration 实例注册信息
     * @return 注册结果
     */
    public SfcLocalRegisterResult register(SfcLocalRegistration registration) {
        SfcLocalRegisterRequest request = new SfcLocalRegisterRequest();
        request.setServiceId(registration.getServiceId());
        request.setInstanceId(registration.getInstanceId());
        request.setHost(registration.getHost());
        request.setPort(registration.getPort());
        request.setSecure(registration.isSecure());
        request.setMetadata(registration.getMetadata());
        return request("POST", SfcLocalRegistryConstants.PATH_REGISTER, request,
                new TypeReference<SfcLocalRegistryEnvelope<SfcLocalRegisterResult>>() {});
    }

    /**
     * 发送心跳续约。
     *
     * @param serviceId  服务名
     * @param instanceId 实例 ID
     * @return 服务端返回的心跳超时秒数
     */
    public long heartbeat(String serviceId, String instanceId) {
        SfcLocalInstanceRef request = new SfcLocalInstanceRef();
        request.setServiceId(serviceId);
        request.setInstanceId(instanceId);
        Long ttl = request("POST", SfcLocalRegistryConstants.PATH_HEARTBEAT, request,
                new TypeReference<SfcLocalRegistryEnvelope<Long>>() {});
        return ttl == null ? 0 : ttl;
    }

    /**
     * 注销实例。
     *
     * @param serviceId  服务名
     * @param instanceId 实例 ID
     */
    public void deregister(String serviceId, String instanceId) {
        SfcLocalInstanceRef request = new SfcLocalInstanceRef();
        request.setServiceId(serviceId);
        request.setInstanceId(instanceId);
        request("POST", SfcLocalRegistryConstants.PATH_DEREGISTER, request,
                new TypeReference<SfcLocalRegistryEnvelope<Void>>() {});
    }

    /**
     * 查询服务实例。
     *
     * @param serviceId 服务名
     * @return 实例列表
     */
    public List<SfcLocalServiceInstance> getInstances(String serviceId) {
        String path = SfcLocalRegistryConstants.PATH_INSTANCES
                + "?serviceId=" + URLEncoder.encode(serviceId, StandardCharsets.UTF_8);
        List<SfcLocalServiceInstance> instances = request("GET", path, null,
                new TypeReference<SfcLocalRegistryEnvelope<List<SfcLocalServiceInstance>>>() {});
        return instances == null ? List.of() : instances;
    }

    /**
     * 查询所有服务名。
     *
     * @return 服务名列表
     */
    public List<String> getServices() {
        List<String> services = request("GET", SfcLocalRegistryConstants.PATH_SERVICES, null,
                new TypeReference<SfcLocalRegistryEnvelope<List<String>>>() {});
        return services == null ? List.of() : services;
    }

    /**
     * 异步查询服务实例。
     * <p>
     * 供反应式发现实现使用：不阻塞调用线程，请求由 JDK {@link HttpClient#sendAsync} 执行，
     * 异常以失败 future 的形式返回（解析后的业务异常为 {@link SfcLocalRegistryException}）。
     *
     * @param serviceId 服务名
     * @return 异步结果，实例列表非 null
     */
    public CompletableFuture<List<SfcLocalServiceInstance>> getInstancesAsync(String serviceId) {
        String path = SfcLocalRegistryConstants.PATH_INSTANCES
                + "?serviceId=" + URLEncoder.encode(serviceId, StandardCharsets.UTF_8);
        return requestAsync(path, new TypeReference<SfcLocalRegistryEnvelope<List<SfcLocalServiceInstance>>>() {})
                .thenApply(instances -> instances == null ? List.of() : instances);
    }

    /**
     * 异步查询所有服务名。
     * <p>
     * 供反应式发现实现使用：不阻塞调用线程，异常以失败 future 的形式返回。
     *
     * @return 异步结果，服务名列表非 null
     */
    public CompletableFuture<List<String>> getServicesAsync() {
        return requestAsync(SfcLocalRegistryConstants.PATH_SERVICES,
                new TypeReference<SfcLocalRegistryEnvelope<List<String>>>() {})
                .thenApply(services -> services == null ? List.of() : services);
    }

    /**
     * 执行一次 API 请求并解析响应信封。
     *
     * @param method   HTTP 方法
     * @param path     相对路径
     * @param body     请求体，GET 时为 null
     * @param typeRef  响应信封类型
     * @param <T>      业务数据类型
     * @return 业务数据
     */
    private <T> T request(String method, String path, Object body,
                          TypeReference<SfcLocalRegistryEnvelope<T>> typeRef) {
        try {
            HttpResponse<String> response = httpClient.send(buildRequest(method, path, body),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return handleResponse(response, typeRef);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SfcLocalRegistryException(500, "调用注册中心被中断");
        } catch (IOException e) {
            throw new SfcLocalRegistryException(500, "调用注册中心失败：" + e.getMessage());
        }
    }

    /**
     * 异步执行一次 GET 查询并解析响应信封。
     *
     * @param path    相对路径
     * @param typeRef 响应信封类型
     * @param <T>     业务数据类型
     * @return 异步业务数据
     */
    private <T> CompletableFuture<T> requestAsync(String path,
                                                  TypeReference<SfcLocalRegistryEnvelope<T>> typeRef) {
        HttpRequest httpRequest;
        try {
            httpRequest = buildRequest("GET", path, null);
        } catch (IOException e) {
            return CompletableFuture.failedFuture(
                    new SfcLocalRegistryException(500, "调用注册中心失败：" + e.getMessage()));
        }
        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> handleResponse(response, typeRef));
    }

    /**
     * 构造 API 请求，拼接注册中心地址、超时、密码头与 JSON 请求体。
     *
     * @param method HTTP 方法
     * @param path   相对路径
     * @param body   请求体，GET 时为 null
     * @return HTTP 请求
     * @throws IOException 请求体序列化失败
     */
    private HttpRequest buildRequest(String method, String path, Object body) throws IOException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(properties.getReadTimeout() == null ? Duration.ofSeconds(5) : properties.getReadTimeout())
                .header("Accept", "application/json");
        if (hasText(properties.getPassword())) {
            builder.header(SfcLocalRegistryConstants.PASSWORD_HEADER, properties.getPassword());
        }
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json");
            builder.method(method, HttpRequest.BodyPublishers.ofString(
                    objectMapper.writeValueAsString(body), StandardCharsets.UTF_8));
        }
        return builder.build();
    }

    /**
     * 解析响应信封并映射为业务数据；非 2xx 或信封业务码非 200 时抛出业务异常。
     *
     * @param response HTTP 响应
     * @param typeRef  响应信封类型
     * @param <T>      业务数据类型
     * @return 业务数据
     */
    private <T> T handleResponse(HttpResponse<String> response,
                                 TypeReference<SfcLocalRegistryEnvelope<T>> typeRef) {
        SfcLocalRegistryEnvelope<T> envelope = parseEnvelope(response.body(), typeRef);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            int code = envelope == null ? response.statusCode() : envelope.getCode();
            String message = envelope == null ? "HTTP " + response.statusCode() : envelope.getMsg();
            throw new SfcLocalRegistryException(code, response.statusCode(), message);
        }
        if (envelope == null) {
            throw new SfcLocalRegistryException(500, response.statusCode(), "注册中心返回了空响应");
        }
        if (envelope.getCode() != 200) {
            throw new SfcLocalRegistryException(envelope.getCode(), response.statusCode(), envelope.getMsg());
        }
        return envelope.getData();
    }

    /**
     * 解析响应信封；响应体不是合法信封时返回 null。
     *
     * @param body    响应体
     * @param typeRef 信封类型
     * @param <T>     业务数据类型
     * @return 响应信封或 null
     */
    private <T> SfcLocalRegistryEnvelope<T> parseEnvelope(String body,
                                                          TypeReference<SfcLocalRegistryEnvelope<T>> typeRef) {
        if (!hasText(body)) {
            return null;
        }
        try {
            return objectMapper.readValue(body, typeRef);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * 拼接注册中心 API 根地址。
     *
     * @param properties 客户端配置
     * @return 根地址
     */
    private static String buildBaseUrl(SfcLocalRegistryProperties properties) {
        String serverAddr = properties.getServerAddr();
        if (!hasText(serverAddr)) {
            throw new IllegalArgumentException("spring.cloud.sfc-local.server-addr 不能为空");
        }
        String base = serverAddr.trim();
        if (!base.startsWith("http://") && !base.startsWith("https://")) {
            base = (properties.isSecure() ? "https://" : "http://") + base;
        }
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String contextPath = properties.getContextPath();
        if (hasText(contextPath)) {
            contextPath = contextPath.trim();
            if (!contextPath.startsWith("/")) {
                contextPath = "/" + contextPath;
            }
            while (contextPath.endsWith("/")) {
                contextPath = contextPath.substring(0, contextPath.length() - 1);
            }
            base += contextPath;
        }
        return base + SfcLocalRegistryConstants.API_PREFIX;
    }

    /**
     * 判断字符串非 null 且包含非空白字符。
     *
     * @param value 待判断字符串
     * @return 非空白时返回 true
     */
    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
