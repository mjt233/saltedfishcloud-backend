package com.sfc.webshell.controller;

import com.sfc.webshell.service.ShellExecutor;
import com.xiaotao.saltedfishcloud.utils.SpringContextUtils;
import org.springframework.stereotype.Component;

import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ServerEndpoint("/api/webshell/{sessionId}")
public class WebShellEndpointHandler {
    private ShellExecutor shellExecutor;

    /**
     * key - websocket sessionId, value - 消息队列订阅id
     */
    private static final Map<String, Long> subscribeIdMap = new ConcurrentHashMap<>();

    private ShellExecutor getShellExecutor() {
        if (shellExecutor == null) {
            shellExecutor = SpringContextUtils.getContext().getBean(ShellExecutor.class);
        }
        return shellExecutor;
    }

    @OnOpen
    public void onOpen(Session session, @PathParam("sessionId") Long sessionId) {
        long subscribeId = getShellExecutor().subscribeOutput(sessionId, msg -> session.getAsyncRemote().sendText(msg));
        subscribeIdMap.put(session.getId(), subscribeId);
    }

    @OnClose
    public void onClose(Session session) {
        Long subscribeId = subscribeIdMap.get(session.getId());
        if (subscribeId != null) {
            getShellExecutor().unsubscribeOutput(subscribeId);
        }
    }

    @OnMessage
    public void onMessage(String message, @PathParam("sessionId") Long sessionId) throws IOException {
        getShellExecutor().writeInput(sessionId, message);
    }
}
