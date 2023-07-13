package com.sfc.webshell.service;

import com.sfc.webshell.model.ShellExecuteParameter;
import com.sfc.webshell.model.ShellExecuteResult;
import com.sfc.webshell.model.ShellSessionRecord;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

public interface ShellExecutor {

    /**
     * 简单执行shell命令
     * @param nodeId    指定执行节点id，若为null则表示当前节点
     * @param parameter   待执行的命令cams
     * @return      执行结果
     */
    ShellExecuteResult executeCommand(Long nodeId, ShellExecuteParameter parameter) throws IOException;

    /**
     * 创建一个可交互的shell会话
     * @param nodeId        执行节点
     * @param parameter     初始化参数
     * @return              shell会话记录
     */
    ShellSessionRecord createSession(Long nodeId, ShellExecuteParameter parameter) throws IOException;

    /**
     * 获取在当前主机运行的会话shell的进程
     * @param sessionId    会话实例id
     * @return             进程
     */
    Process getProcess(Long sessionId);

    /**
     * 杀掉一个在当前主机运行的shell会话进程
     * @param sessionId 会话实例id
     * @param forceDelay    强制kill延迟，单位ms。若普通的kill执行后进程仍未退出，在经过延迟后将会被强制kill。若小于等于0则表示不使用强制kill机制
     */
    void killLocal(Long sessionId, long forceDelay);

    /**
     * 杀掉一个在当前集群中运行的shell会话进程
     * @param sessionId     会话实例id
     * @param forceDelay    强制kill延迟，单位ms。若普通的kill执行后进程仍未退出，在经过延迟后将会被强制kill。若小于等于0则表示不使用强制kill机制
     */
    void kill(Long sessionId, long forceDelay) throws IOException;

    /**
     * 修改shell会话的pty终端尺寸
     * @param sessionId 会话id
     * @param rows      行
     * @param cols      列
     */
    void resizePty(Long sessionId, int rows, int cols) throws IOException;

    /**
     * 获取所有当前主机运行的webShell进程会话信息
     */
    List<ShellSessionRecord> getLocalSession();

    /**
     * 获取当前集群所有节点运行的webShell进程会话信息
     */
    List<ShellSessionRecord> getAllSession() throws IOException;

    /**
     * 获取会话最近的输出日志（若消息过长，较老的输出会可能被丢弃）
     * @param sessionId     会话id
     * @return              近期历史输出
     */
    String getLog(Long sessionId) throws IOException;

    /**
     * 修改会话名称
     * @param sessionId 会话id
     * @param newName 新名称
     */
    void rename(Long sessionId, String newName);

    /**
     * 向shell会话输入写入内容
     * @param sessionId 会话id
     * @param input     需要输入到标准输入的字符
     */
    void writeInput(Long sessionId, String input) throws IOException;

    /**
     * 订阅web shell输出
     * @param sessionId 会话id
     * @param consumer  输出消费函数（注意，不一定是按行消费）
     * @return          订阅id
     */
    long subscribeOutput(Long sessionId, Consumer<String> consumer);

    /**
     * 取消输出订阅，注意订阅与取消订阅要在同一个服务实例上调用，取消订阅操作才能成功
     * @param subscribeId   订阅id
     */
    void unsubscribeOutput(Long subscribeId);

    /**
     * 重启会话
     * @param sessionId 会话id
     */
    void restart(Long sessionId) throws IOException;

    /**
     * 移除会话。若会话运行中则会先对其执行kill。
     * @param sessionId 会话id
     */
    void remove(Long sessionId) throws IOException;
}
