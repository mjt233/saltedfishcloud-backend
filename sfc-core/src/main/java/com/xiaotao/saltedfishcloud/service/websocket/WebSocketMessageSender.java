package com.xiaotao.saltedfishcloud.service.websocket;

/**
 * WebSocket 消息发送器，用于向客户端发送消息。
 */
@FunctionalInterface
public interface WebSocketMessageSender {
    /**
     * 发送消息给客户端。
     *
     * @param message 消息内容（JSON 字符串）
     */
    void send(String message);
}
