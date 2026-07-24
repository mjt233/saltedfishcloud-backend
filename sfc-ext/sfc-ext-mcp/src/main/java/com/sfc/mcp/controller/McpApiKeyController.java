package com.sfc.mcp.controller;

import com.sfc.mcp.model.McpApiKey;
import com.sfc.mcp.model.vo.McpApiKeyVO;
import com.sfc.mcp.service.McpApiKeyService;
import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.model.po.UserPrincipal;
import com.xiaotao.saltedfishcloud.validator.annotations.UID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * MCP API Key 管理控制器。
 * <p>
 * 提供 API Key 的生成、查询和撤销接口。
 * 生成时返回原始 token（仅此一次），后续仅可查看脱敏版本。
 */
@RestController
@RequestMapping("/api/mcp/apiKey")
@Tag(name = "MCP API Key 管理")
@RequiredArgsConstructor
public class McpApiKeyController {

    private final McpApiKeyService mcpApiKeyService;

    /**
     * 查询当前用户的 MCP API Key 列表。
     * <p>
     * 返回结果仅包含脱敏后的展示字段，不包含敏感的 token 哈希信息。
     *
     * @param userPrincipal 当前登录用户
     * @return 当前用户的 API Key 列表
     */
    @Operation(summary = "查询当前用户的MCP API Key列表")
    @GetMapping("/list")
    public JsonResult<List<McpApiKeyVO>> list(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        return JsonResultImpl.getInstance(McpApiKeyVO.from(mcpApiKeyService.listByUid(userPrincipal.getId())));
    }

    /**
     * 新生成当前用户的 MCP API Key。
     *
     * @param name Key 名称（可选，默认 "default"）
     * @return 生成的原始API Key
     */
    @Operation(summary = "生成MCP API Key")
    @PostMapping("/generate")
    public JsonResult<String> generate(
            @RequestParam(value = "name", defaultValue = "default") String name,
            @AuthenticationPrincipal UserPrincipal userPrincipal
            ) {
        return JsonResultImpl.getInstance(mcpApiKeyService.generate(userPrincipal.getId(), name));
    }

    /**
     * 撤销当前用户的 MCP API Key。
     *
     * @param id  API Key 主键 ID
     * @return 操作结果
     */
    @Operation(summary = "撤销MCP API Key")
    @PostMapping("/delete")
    public JsonResult<Object> delete(@RequestParam("id") Long id,@AuthenticationPrincipal UserPrincipal userPrincipal) {
        validateOwnership(id, userPrincipal.getId());
        mcpApiKeyService.deleteApiKey(id);
        return JsonResult.emptySuccess();
    }

    /**
     * 重命名当前用户的 MCP API Key。
     *
     * @param id   API Key 主键 ID
     * @param name 新名称
     * @return 操作结果
     */
    @Operation(summary = "重命名MCP API Key")
    @PostMapping("/rename")
    public JsonResult<Object> rename(
            @RequestParam("id") Long id,
            @RequestParam("name") String name,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        validateOwnership(id, userPrincipal.getId());
        mcpApiKeyService.rename(id, name);
        return JsonResult.emptySuccess();
    }

    /**
     * 校验 API Key 的归属权。
     *
     * @param keyId API Key 主键 ID
     * @param uid   当前用户 ID
     */
    private void validateOwnership(Long keyId, Long uid) {
        McpApiKey apiKey = mcpApiKeyService.getById(keyId);
        if (!apiKey.getUid().equals(uid)) {
            throw new IllegalArgumentException("无权操作该 API Key");
        }
    }
}
