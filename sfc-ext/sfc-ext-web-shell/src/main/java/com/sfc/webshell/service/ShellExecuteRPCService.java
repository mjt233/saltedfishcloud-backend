package com.sfc.webshell.service;

import com.sfc.rpc.annotation.RPCAction;
import com.sfc.rpc.annotation.RPCService;
import com.sfc.rpc.enums.RPCResponseStrategy;
import com.sfc.webshell.model.ShellSessionRecord;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@RPCService(namespace = "web_shell")
public interface ShellExecuteRPCService {

    /**
     * 重启会话
     * @param sessionId 会话id
     */
    @RPCAction
    void restart(Long sessionId) throws IOException;

    /**
     * 移除会话。若会话运行中则会先对其执行kill。
     * @param sessionId 会话id
     */
    @RPCAction
    void remove(Long sessionId) throws IOException;


    /**
     * 杀掉一个在当前集群中运行的shell会话进程
     * @param sessionId     会话实例id
     * @param forceDelay    强制kill延迟，单位ms。若普通的kill执行后进程仍未退出，在经过延迟后将会被强制kill。若小于等于0则表示不使用强制kill机制
     */
    @RPCAction
    void kill(Long sessionId, long forceDelay);

    /**
     * 修改shell会话的pty终端尺寸
     * @param sessionId 会话id
     * @param rows      行
     * @param cols      列
     */
    @RPCAction
    void resizePty(Long sessionId, int rows, int cols) throws IOException;

    /**
     * 获取当前集群所有节点运行的webShell进程会话信息
     */
    @RPCAction(strategy = RPCResponseStrategy.SUMMARY_ALL)
    List<ShellSessionRecord> getAllSession() throws IOException;

    /**
     * 获取会话最近的输出日志（若消息过长，较老的输出会可能被丢弃）
     * @param sessionId     会话id
     * @return              近期历史输出
     */
    @RPCAction
    String getLog(Long sessionId) throws IOException;

    /**
     * 修改会话名称
     * @param sessionId 会话id
     * @param newName 新名称
     */
    @RPCAction
    void rename(Long sessionId, String newName);


    /**
     * 根据会话id获取会话信息
     * @param sessionId 会话id
     * @return          会话信息
     */
    @RPCAction
    ShellSessionRecord getSessionById(Long sessionId) throws IOException;

}
