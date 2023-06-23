package com.sfc.webshell.service;

import com.sfc.webshell.model.ShellExecuteParameter;
import com.sfc.webshell.model.ShellExecuteResult;

import java.io.IOException;

public interface ShellExecutor {

    /**
     * 简单执行shell命令
     * @param nodeId    指定执行节点id，若为null则表示当前节点
     * @param cmd   待执行的命令
     * @return      执行结果
     */
    ShellExecuteResult executeCommand(Long nodeId, ShellExecuteParameter parameter) throws IOException;
}
