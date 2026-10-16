package com.sfc.webshell.controller;

import com.sfc.rpc.annotation.RPCResource;
import com.sfc.webshell.model.ShellExecuteParameter;
import com.sfc.webshell.model.ShellSessionRecord;
import com.sfc.webshell.service.ShellExecuteRPCService;
import com.sfc.webshell.service.ShellExecuteService;
import com.sfc.webshell.model.ShellExecuteResult;
import com.sfc.cloud.model.json.SfcJsonResult;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.Min;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/webShell")
public class WebShellController {
    @RPCResource
    private ShellExecuteRPCService rpcService;

    @Autowired
    private ShellExecuteService service;

    /**
     * 直接执行简单命令
     * @param nodeId    节点id，留空则表示使用当前节点
     * @param parameter   执行参数
     */
    @PostMapping("/executeCommand")
    @RolesAllowed("ADMIN")
    public SfcJsonResult<ShellExecuteResult> executeCommand(@RequestParam(value = "nodeId", required = false) Long nodeId,
                                                         @RequestBody ShellExecuteParameter parameter) throws IOException {
        return SfcJsonResult.getInstance(service.executeCommand(nodeId, parameter));
    }

    /**
     * 创建可交互式shell会话
     * @param nodeId    节点id，留空则表示使用当前节点
     * @param parameter 会话参数
     */
    @PostMapping("/createSession")
    @RolesAllowed("ADMIN")
    public SfcJsonResult<ShellSessionRecord> createSession(@RequestParam(value = "nodeId", required = false) Long nodeId,
                                                        @RequestBody ShellExecuteParameter parameter) throws IOException {
        return SfcJsonResult.getInstance(service.createSession(nodeId, parameter));
    }

    @GetMapping("/listSession")
    @RolesAllowed("ADMIN")
    public SfcJsonResult<List<ShellSessionRecord>> listSession() throws IOException {
        return SfcJsonResult.getInstance(rpcService.getAllSession());
    }

    @GetMapping("/getLog")
    @RolesAllowed("ADMIN")
    public SfcJsonResult<String> getLog(@RequestParam("sessionId") Long sessionId) throws IOException {
        return SfcJsonResult.getInstance(rpcService.getLog(sessionId));
    }

    @GetMapping("/rename")
    @RolesAllowed("ADMIN")
    public SfcJsonResult<?> rename(@RequestParam("sessionId") Long sessionId, @RequestParam("name") String newName) {
        rpcService.rename(sessionId, newName);
        return SfcJsonResult.emptySuccess();
    }

    @GetMapping("/restart")
    @RolesAllowed("ADMIN")
    public SfcJsonResult<?> restart(@RequestParam("sessionId") Long sessionId) throws IOException {
        rpcService.restart(sessionId);
        return SfcJsonResult.emptySuccess();
    }

    @GetMapping("/resizePty")
    @RolesAllowed("ADMIN")
    public SfcJsonResult<?> resizePty(@RequestParam("sessionId") Long sessionId,
                                   @RequestParam("rows") @Min(1) Integer rows,
                                   @RequestParam("cols") @Min(1) Integer cols) throws IOException {
        rpcService.resizePty(sessionId, rows, cols);
        return SfcJsonResult.emptySuccess();
    }

    @GetMapping("/remove")
    @RolesAllowed("ADMIN")
    public SfcJsonResult<?> remove(@RequestParam("sessionId") Long sessionId) throws IOException {
        rpcService.remove(sessionId);
        return SfcJsonResult.emptySuccess();
    }

    @GetMapping("/kill")
    @RolesAllowed("ADMIN")
    public SfcJsonResult<?> kill(@RequestParam("sessionId") Long sessionId) throws IOException {
        rpcService.kill(sessionId, 60000);
        return SfcJsonResult.emptySuccess();
    }


}
