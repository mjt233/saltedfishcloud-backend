package com.sfc.webshell.controller;

import com.sfc.webshell.constans.WebShellMQTopic;
import com.sfc.webshell.service.ShellExecutor;
import com.xiaotao.saltedfishcloud.service.MQService;
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

    private MQService mqService;

    private MQService getMqService() {
        if (mqService == null) {
            mqService = SpringContextUtils.getContext().getBean(MQService.class);
        }
        return mqService;
    }

    private boolean isClosed = false;

    /**
     * key - websocket sessionId, value - 消息队列订阅id
     */
    private static final Map<String, Long> subscribeIdMap = new ConcurrentHashMap<>();
    private static final Map<String, Long> broadcastSubscribeIdMap = new ConcurrentHashMap<>();

    private ShellExecutor getShellExecutor() {
        if (shellExecutor == null) {
            shellExecutor = SpringContextUtils.getContext().getBean(ShellExecutor.class);
        }
        return shellExecutor;
    }

    @OnOpen
    public void onOpen(Session session, @PathParam("sessionId") Long sessionId) {
        long mqSubscribeId = getShellExecutor().subscribeOutput(sessionId,  msg ->  {
            synchronized (session) {
                session.getAsyncRemote().sendText(msg);
            }
        });
        long broadcastSubscribeId = getMqService().subscribeBroadcast(WebShellMQTopic.Prefix.EXIT_BROADCAST + sessionId, msg -> {
            isClosed = true;
            getMqService().unsubscribeMessageQueue(mqSubscribeId);
            getMqService().unsubscribe(broadcastSubscribeIdMap.get(session.getId()));
            try {
                session.close();
            } catch (IOException ignore) { }


        });
        subscribeIdMap.put(session.getId(), mqSubscribeId);
        broadcastSubscribeIdMap.put(session.getId(), broadcastSubscribeId);
    }

    @OnClose
    public void onClose(Session session) {
        if (isClosed) {
            return;
        }
        Long subscribeId = subscribeIdMap.get(session.getId());
        Long broadcastSubscribeId = broadcastSubscribeIdMap.get(session.getId());
        if (subscribeId != null) {
            getShellExecutor().unsubscribeOutput(subscribeId);
        }
        if (broadcastSubscribeId != null) {
            getMqService().unsubscribe(broadcastSubscribeId);
        }
    }

    @OnMessage
    public void onMessage(String message, @PathParam("sessionId") Long sessionId) throws IOException {
        getShellExecutor().writeInput(sessionId, message);
    }
}
