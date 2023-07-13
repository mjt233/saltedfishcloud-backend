package com.sfc.webshell.controller;

import com.sfc.webshell.model.ShellExecuteParameter;
import com.sfc.webshell.model.ShellSessionRecord;
import com.sfc.webshell.service.ShellExecutor;
import com.sfc.webshell.model.ShellExecuteResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.annotation.security.RolesAllowed;
import javax.validation.constraints.Min;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/webShell")
public class WebShellController {
    @Autowired
    private ShellExecutor shellExecutor;

    /**
     * 直接执行简单命令
     * @param nodeId    节点id，留空则表示使用当前节点
     * @param parameter   执行参数
     */
    @PostMapping("/executeCommand")
    @RolesAllowed("ADMIN")
    public JsonResult<ShellExecuteResult> executeCommand(@RequestParam(value = "nodeId", required = false) Long nodeId,
                                                         @RequestBody ShellExecuteParameter parameter) throws IOException {
        return JsonResultImpl.getInstance(shellExecutor.executeCommand(nodeId, parameter));
    }

    /**
     * 创建可交互式shell会话
     * @param nodeId    节点id，留空则表示使用当前节点
     * @param parameter 会话参数
     */
    @PostMapping("/createSession")
    @RolesAllowed("ADMIN")
    public JsonResult<ShellSessionRecord> createSession(@RequestParam(value = "nodeId", required = false) Long nodeId,
                                                        @RequestBody ShellExecuteParameter parameter) throws IOException {
        return JsonResultImpl.getInstance(shellExecutor.createSession(nodeId, parameter));
    }

    @GetMapping("/listSession")
    @RolesAllowed("ADMIN")
    public JsonResult<List<ShellSessionRecord>> listSession() throws IOException {
        return JsonResultImpl.getInstance(shellExecutor.getAllSession());
    }

    @GetMapping("/getLog")
    @RolesAllowed("ADMIN")
    public JsonResult<String> getLog(@RequestParam("sessionId") Long sessionId) throws IOException {
        return JsonResultImpl.getInstance(shellExecutor.getLog(sessionId));
    }

    @GetMapping("/rename")
    @RolesAllowed("ADMIN")
    public JsonResult<?> rename(@RequestParam("sessionId") Long sessionId, @RequestParam("name") String newName) {
        shellExecutor.rename(sessionId, newName);
        return JsonResult.emptySuccess();
    }

    @GetMapping("/restart")
    @RolesAllowed("ADMIN")
    public JsonResult<?> restart(@RequestParam("sessionId") Long sessionId) throws IOException {
        shellExecutor.restart(sessionId);
        return JsonResult.emptySuccess();
    }

    @GetMapping("/resizePty")
    @RolesAllowed("ADMIN")
    public JsonResult<?> resizePty(@RequestParam("sessionId") Long sessionId,
                                   @RequestParam("rows") @Min(1) Integer rows,
                                   @RequestParam("cols") @Min(1) Integer cols) throws IOException {
        shellExecutor.resizePty(sessionId, rows, cols);
        return JsonResult.emptySuccess();
    }

    @GetMapping("/remove")
    @RolesAllowed("ADMIN")
    public JsonResult<?> remove(@RequestParam("sessionId") Long sessionId) throws IOException {
        shellExecutor.remove(sessionId);
        return JsonResult.emptySuccess();
    }

    @GetMapping("/kill")
    @RolesAllowed("ADMIN")
    public JsonResult<?> kill(@RequestParam("sessionId") Long sessionId) throws IOException {
        shellExecutor.kill(sessionId, 60000);
        return JsonResult.emptySuccess();
    }


}
