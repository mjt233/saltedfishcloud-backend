package com.sfc.webshell.service;

import com.sfc.webshell.model.ShellExecuteParameter;
import com.sfc.webshell.model.ShellExecuteResult;
import com.sfc.webshell.model.ShellSessionRecord;

import java.io.IOException;
import java.util.function.Consumer;

public interface ShellExecuteService extends ShellExecuteRPCService {

    /**
     * 获取在当前主机运行的会话shell的进程
     * @param sessionId    会话实例id
     * @return             进程
     */
    Process getProcess(Long sessionId);


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
}
