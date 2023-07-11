package com.sfc.webshell.service;

import com.sfc.webshell.model.po.ShellExecuteRecord;
import com.xiaotao.saltedfishcloud.service.CrudService;

public interface ShellExecuteRecordService extends CrudService<ShellExecuteRecord> {
    /**
     * 添加一条命令执行记录
     * @param workDir 工作路径
     * @param cmd   执行的命令
     */
    void addCmdRecord(String workDir, String cmd);
}
