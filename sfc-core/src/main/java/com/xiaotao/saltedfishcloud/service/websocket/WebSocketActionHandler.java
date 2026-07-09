package com.xiaotao.saltedfishcloud.service.websocket;

import com.xiaotao.saltedfishcloud.model.websocket.WebSocketRequest;
import jakarta.websocket.Session;

/**
 * WebSocket 动作处理器接口，策略模式定义。
 * <p>
 * 实现类需通过 {@code @Component} 注册为 Spring Bean，
 * 会被 {@link WebSocketHandler} 自动收集并注册到动作分发表。
 */
public interface WebSocketActionHandler {

    /**
     * 获取该处理器支持的 WebSocket 动作。
     *
     * @return 动作标识，对应 {@link WebSocketRequest#getAction()}
     */
    String getAction();

    /**
     * 处理 WebSocket 动作请求。
     *
     * @param request 客户端请求
     * @param session WebSocket 会话，可用于获取用户身份信息
     * @param sender  消息发送器，用于向客户端发送响应
     */
    void handle(WebSocketRequest request, Session session, WebSocketMessageSender sender);
}
