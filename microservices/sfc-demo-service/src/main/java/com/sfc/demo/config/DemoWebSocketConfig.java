package com.sfc.demo.config;

import com.sfc.demo.ws.DemoWsEchoHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 探针的注册配置。
 *
 * <h3>为什么用 Spring 的 {@code WebSocketConfigurer} 而不是 jakarta 的 {@code @ServerEndpoint}</h3>
 * <p>
 * 探针需要读网关注入的 {@code X-Sfc-Uid} <b>请求头</b>，而该头只在【握手阶段】可读。
 * Spring 的 {@link HandshakeInterceptor} 恰好能拿到握手请求，这是最直接的读取点；
 * jakarta 的 {@code @ServerEndpoint} 只有在自定义 {@code ServerEndpointConfig.Configurator} 时才可读，
 * 而 Spring 的 {@code ServerEndpointExporter} 注册 Spring 托管端点时<b>不会</b>使用注解上声明的 configurator
 * （实测：`modifyHandshake` 根本不会被调用）。两者取舍后选前者。
 * </p>
 */
@Configuration
@EnableWebSocket
public class DemoWebSocketConfig implements WebSocketConfigurer {

    /** WebSocket 探针路径（与 REST 探针同前缀） */
    public static final String WS_PATH = "/api/demo/ws";

    /** 网关注入的用户身份头（框架期信任模式） */
    private static final String HEADER_UID = "X-Sfc-Uid";

    /** 回显处理器 */
    private final DemoWsEchoHandler demoWsEchoHandler;

    /**
     * 构造器注入。
     *
     * @param demoWsEchoHandler 回显处理器
     */
    public DemoWebSocketConfig(DemoWsEchoHandler demoWsEchoHandler) {
        this.demoWsEchoHandler = demoWsEchoHandler;
    }

    /**
     * 注册 WebSocket 处理器与握手拦截器。
     *
     * @param registry 处理器注册表
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(demoWsEchoHandler, WS_PATH)
                // 探针允许任意来源：它只用于本机验收，部署约束是"微服务只在内网暴露"
                .setAllowedOriginPatterns("*")
                .addInterceptors(new UidHandshakeInterceptor());
    }

    /**
     * 握手拦截器：把网关注入的 {@code X-Sfc-Uid} 请求头捕获进会话属性，供消息处理阶段读取。
     */
    static class UidHandshakeInterceptor implements HandshakeInterceptor {

        /**
         * 握手前：复制身份头。
         *
         * @param request  握手请求
         * @param response 握手响应
         * @param wsHandler 目标处理器
         * @param attributes 会话属性（写入后可在业务方法里读到）
         * @return 恒为 true（探针不做拒绝逻辑）
         */
        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                       WebSocketHandler wsHandler, Map<String, Object> attributes) {
            String uid = request.getHeaders().getFirst(HEADER_UID);
            if (uid != null) {
                attributes.put(DemoWsEchoHandler.ATTR_UID, uid);
            }
            return true;
        }

        /**
         * 握手后：无需处理。
         *
         * @param request  握手请求
         * @param response 握手响应
         * @param wsHandler 目标处理器
         * @param exception 握手异常，成功时为 null
         */
        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Exception exception) {
            // 探针无需处理
        }
    }
}
