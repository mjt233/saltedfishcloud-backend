package com.sfc.demo.ws;

import com.sfc.demo.controller.DemoController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * WebSocket 回显探针：验证网关对 WebSocket 的【升级握手 ＋ 双向帧】转发。
 *
 * <h3>为什么需要它</h3>
 * <p>
 * 整站唯一入口必须能转发 WebSocket（core 侧有 {@code /api/ws} 等端点），而 Spring Cloud Gateway 的
 * MVC 变体没有 WebSocket 转发能力——这是选择 WebFlux 变体的唯一硬理由。core 的 {@code /api/ws}
 * 只能证明"握手能过"；要证明"服务端主动发的帧能原路回到客户端"，需要一个会主动回话的端点：
 * </p>
 * <ul>
 *     <li>连接建立后服务端先主动推一条 {@code hello}（验证服务端 → 客户端方向）；</li>
 *     <li>客户端每发一条文本消息，服务端回一条 {@code echo:{原文}:uid={X-Sfc-Uid}}（验证客户端 → 服务端方向）。</li>
 * </ul>
 *
 * <h3>身份读取</h3>
 * <p>
 * uid 只来自网关注入的 {@code X-Sfc-Uid} <b>请求头</b>，且只能在握手阶段读取（WebSocket 会话建立后
 * 就没有"请求头"了）——因此由 {@link DemoWebSocketConfig} 里的握手拦截器把它放进会话属性。
 * </p>
 * <p>
 * ⚠️ <b>硬约束</b>：绝<b>不</b>从查询参数里读 {@code uid}——那正是"篡改 uid 越权"的入口。
 * </p>
 */
@Component
public class DemoWsEchoHandler extends TextWebSocketHandler {

    /** 日志（本工程刻意不引入 Lombok：零依赖的独立微服务示例，只用 Spring 自带能力） */
    private static final Logger log = LoggerFactory.getLogger(DemoWsEchoHandler.class);

    /** 握手阶段写入会话属性的键名：网关注入的用户身份 */
    public static final String ATTR_UID = "sfcUid";

    /** 本实例端口，用于在验收中区分请求落到了哪个实例 */
    @Value("${server.port:0}")
    private int serverPort;

    /**
     * 连接建立：主动推一条 hello，取证"服务端推送"方向。
     *
     * @param session 会话
     * @throws Exception 发送失败时抛出
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("[demo-ws] 连接建立 session={} port={} uid={}", session.getId(), serverPort,
                session.getAttributes().get(ATTR_UID));
        session.sendMessage(new TextMessage("hello:port=" + serverPort + ":uid=" + safeUid(session)));
    }

    /**
     * 收到文本消息：原样回显，并附带握手阶段捕获的网关注入身份。
     *
     * @param session 会话
     * @param message 消息
     * @throws Exception 发送失败时抛出
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        session.sendMessage(new TextMessage("echo:" + message.getPayload() + ":uid=" + safeUid(session)
                + ":port=" + serverPort));
    }

    /**
     * 连接关闭。
     *
     * @param session 会话
     * @param status  关闭状态
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("[demo-ws] 连接关闭 session={} status={}", session.getId(), status);
    }

    /**
     * 取会话中捕获的 uid。
     *
     * @param session 会话
     * @return uid，缺失时返回 {@code <none>}
     */
    private String safeUid(WebSocketSession session) {
        Object uid = session.getAttributes().get(ATTR_UID);
        return uid == null ? "<none>" : String.valueOf(uid);
    }
}
