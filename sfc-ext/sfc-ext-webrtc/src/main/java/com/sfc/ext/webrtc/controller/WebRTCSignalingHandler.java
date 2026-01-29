package com.sfc.ext.webrtc.controller;

import com.sfc.ext.webrtc.enums.MessageDataType;
import com.sfc.ext.webrtc.model.RTCSignalingMessage;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import com.xiaotao.saltedfishcloud.utils.TypeUtils;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@ServerEndpoint("/api/webrtc/ws/{peerId}")
public class WebRTCSignalingHandler {
    private final static String LOG_PREFIX = "[WebRTC]";
    /**
     * 记录WebSocket会话对应的实际peer id
     * key - session的id
     * value - 改会话自身的peer id标识
     */
    private static final Map<String, String> SESSION_PEER_MAP = new ConcurrentHashMap<>();

    /**
     * key - peer id标识
     * value - 对应的WebSocket会话
     */
    private static final Map<String, Session> PEER_ID_TO_SESSION = new ConcurrentHashMap<>();

    /**
     * peer 对端的 peer id
     * key - 本端peer id
     * value - 对端peer id
     */
    private static final Map<String, String> PEER_TO_PEER = new ConcurrentHashMap<>();

    /**
     * 判断是否存在某个端的WS会话标识
     */
    public static boolean isExist(String peerId) {
        return PEER_ID_TO_SESSION.containsKey(peerId);
    }

    @OnOpen
    public void onOpen(Session session, @PathParam("peerId") String peerId) {
        String actualPeerId = peerId;
        while (PEER_ID_TO_SESSION.putIfAbsent(actualPeerId, session) != null) {
            actualPeerId = StringUtils.getRandomString(6);
        }
        log.debug("{} peer接入ws: {}. WebSocket Session ID: {}", LOG_PREFIX, actualPeerId, session.getId());
        SESSION_PEER_MAP.put(session.getId(), actualPeerId);

        // 告知标识
        RTCSignalingMessage msg = new RTCSignalingMessage();
        msg.setData(actualPeerId);
        msg.setDataType(MessageDataType.PEER_ID);
        session.getAsyncRemote().sendText(MapperHolder.toJsonNoEx(msg));
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        try {
            // 1. 解析收到的信令消息
            RTCSignalingMessage signal = MapperHolder.parseJson(message, RTCSignalingMessage.class);

            String peerId = SESSION_PEER_MAP.get(session.getId());
            // 2. 补全发送者 ID（可选，方便对端处理）
            if (signal.getFromPeerId() == null) {
                signal.setFromPeerId(peerId);
            }

            // 3. 找到目标对端
            String targetPeerId = signal.getToPeerId();
            Session targetSession = PEER_ID_TO_SESSION.get(targetPeerId);

            if (targetSession != null && targetSession.isOpen()) {
                // 4. 转发消息给目标用户
                String jsonResponse = MapperHolder.toJson(signal);
                targetSession.getAsyncRemote().sendText(jsonResponse);

                if (signal.getDataType() == MessageDataType.REDIRECT_NEW_PEER) {
                    String newHostPeerId = TypeUtils.toString(signal.getData());
                    String guestPeerId = signal.getToPeerId();
                    String originHostPeerId = signal.getFromPeerId();
                    PEER_TO_PEER.put(guestPeerId, newHostPeerId);
                    PEER_TO_PEER.put(newHostPeerId, guestPeerId);
                    log.debug("{} 通知 {} 转移到新目标会话 {} 原目标会话 {}", LOG_PREFIX, signal.getToPeerId(), newHostPeerId, originHostPeerId);
                } else {
                    log.debug("{} 转发信令: {} -> {}, 类型: {}", LOG_PREFIX, peerId, targetPeerId, signal.getDataType());
                    PEER_TO_PEER.putIfAbsent(peerId, targetPeerId);
                }

            } else {
                log.warn("{} 转发失败: 来自 {},目标用户 {} 不在线", LOG_PREFIX, peerId, targetPeerId);
            }
        } catch (Exception e) {
            log.error("{} 信令处理异常: {}", LOG_PREFIX, e.getMessage());
        }
    }

    @OnClose
    public void onClose(Session session) {
        log.debug("{} WebSocket Session {} 下线", LOG_PREFIX, session.getId());
        String peerId = SESSION_PEER_MAP.get(session.getId());
        String targetPeerId = PEER_TO_PEER.get(peerId);
        Optional.ofNullable(targetPeerId)
                .map(PEER_ID_TO_SESSION::get)
                .filter(Session::isOpen)
                .ifPresent(targetSession -> {
                    try {
                        log.debug("{} 会话端{}下线，给对端{}发送通知", LOG_PREFIX, peerId, targetPeerId);
                        RTCSignalingMessage msg = new RTCSignalingMessage();
                        msg.setFromPeerId(peerId);
                        msg.setToPeerId(targetPeerId);
                        msg.setDataType(MessageDataType.INTERRUPT);
                        targetSession.getAsyncRemote().sendText(MapperHolder.toJson(msg));
                    } catch (IOException e) {
                        log.warn("{} 会话端{}下线，给对端{}发送通知失败 {}", LOG_PREFIX, peerId, targetPeerId, e.getMessage());
                    }
                });
        SESSION_PEER_MAP.remove(session.getId());
        PEER_ID_TO_SESSION.remove(peerId);
        PEER_TO_PEER.remove(peerId);
        log.debug("{} peer离开ws: {}", LOG_PREFIX, peerId);
    }
}
