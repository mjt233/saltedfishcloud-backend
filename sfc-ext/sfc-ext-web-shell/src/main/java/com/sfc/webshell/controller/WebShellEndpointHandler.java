package com.sfc.webshell.controller;

import com.xiaotao.saltedfishcloud.constant.error.CommonError;
import com.sfc.rpc.RPCManager;
import com.sfc.webshell.constans.WebShellMQTopic;
import com.sfc.webshell.model.ShellSessionRecord;
import com.sfc.webshell.service.ShellExecuteRPCService;
import com.sfc.webshell.service.ShellExecuteService;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.model.po.User;
import com.xiaotao.saltedfishcloud.service.mq.MQService;
import com.xiaotao.saltedfishcloud.utils.SpringContextUtils;
import com.xiaotao.saltedfishcloud.validator.UIDValidator;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ServerEndpoint("/api/webshell/{sessionId}")
public class WebShellEndpointHandler {
    private ShellExecuteService shellExecuteService;
    private RPCManager rpcManager;

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

    private ShellExecuteService getShellExecutor() {
        if (shellExecuteService == null) {
            shellExecuteService = SpringContextUtils.getContext().getBean(ShellExecuteService.class);
        }
        return shellExecuteService;
    }

    private RPCManager getRpcManager() {
        if (rpcManager == null) {
            rpcManager = SpringContextUtils.getContext().getBean(RPCManager.class);
        }
        return rpcManager;
    }

    private void auth(Session wsSession, Long sessionId) throws IOException {
        User user = (User) ((UsernamePasswordAuthenticationToken) wsSession.getUserPrincipal()).getPrincipal();
        ShellSessionRecord sessionRecord = getRpcManager()
                .getRPCClient(ShellExecuteRPCService.class)
                .getSessionById(sessionId);
        if (sessionRecord == null) {
            throw  new JsonException("找不到id为" + sessionId + "的webShell交互会话");
        }
        Long shellSessionUid = sessionRecord.getUid();
        if (!UIDValidator.validate(user, shellSessionUid, true)) {
            throw new JsonException(CommonError.SYSTEM_FORBIDDEN);
        }

    }

    @OnOpen
    public void onOpen(Session session, @PathParam("sessionId") Long sessionId) throws IOException {
        try {
            auth(session, sessionId);
        } catch (Throwable e) {
            session.getAsyncRemote().sendText(e.getMessage());
            session.close();
            return;
        }

        long mqSubscribeId = getShellExecutor().subscribeOutput(sessionId,  msg ->  {
            synchronized (session) {
                try {
                    session.getBasicRemote().sendText(msg);
                } catch (IOException ignore) { }
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
