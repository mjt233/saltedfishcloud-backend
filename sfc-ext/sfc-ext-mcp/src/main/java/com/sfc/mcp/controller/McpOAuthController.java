package com.sfc.mcp.controller;

import com.sfc.mcp.constant.McpConstant;
import com.xiaotao.saltedfishcloud.annotations.AllowAnonymous;
import com.xiaotao.saltedfishcloud.cache.CacheService;
import com.xiaotao.saltedfishcloud.dao.jpa.ThirdPartyAppRepo;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyApp;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyAppApiTicket;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyAppApiTicketService;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyAppTokenService;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * MCP OAuth 授权控制器。
 * <p>
 * 提供 MCP 专用的 OAuth 授权接口，支持通过授权码换取永久 ApiTicket，
 * 以及查询当前用户已有的 ApiTicket。
 */
@RestController
@RequestMapping("/api/mcp/oauth")
@Api(tags = "MCP OAuth 授权")
@Slf4j
@RequiredArgsConstructor
public class McpOAuthController {

    private final ThirdPartyAppApiTicketService thirdPartyAppApiTicketService;
    private final ThirdPartyAppTokenService thirdPartyAppTokenService;
    private final ThirdPartyAppRepo thirdPartyAppRepo;
    private final CacheService cacheService;

    /**
     * 查询系统内置 MCP OAuth 应用的 ID。
     * <p>
     * 该 ID 可用于前端发起 OAuth 授权流程时识别系统预置的 MCP 应用。
     *
     * @return MCP OAuth 应用 ID
     */
    @ApiOperation("查询系统MCP OAuth应用ID")
    @GetMapping("/getAppId")
    @AllowAnonymous
    public JsonResult<Long> getAppId() {
        return JsonResultImpl.getInstance(getMcpApp().getId());
    }

    /**
     * 通过授权码换取永久 ApiTicket。
     * <p>
     * 如果该用户已存在 MCP 应用的永久 ApiTicket，旧的会被自动作废。
     *
     * @param code OAuth authorize 重定向返回的授权码
     * @return 完整的 ApiTicket JWT
     */
    @ApiOperation("通过授权码换取永久ApiTicket")
    @GetMapping("/getApiTicket")
    public JsonResult<String> getApiTicket(@RequestParam("code") String code) {
        ThirdPartyApp app = getMcpApp();
        String clientSecret = getClientSecret(app.getId());
        Long uid = SecureUtils.getCurrentUid();

        String accessToken = thirdPartyAppTokenService.getAccessToken(code, clientSecret);
        String apiTicket = thirdPartyAppTokenService.getApiTicket(app.getId(), uid, accessToken, true, true);
        return JsonResultImpl.getInstance(apiTicket);
    }

    /**
     * 查询当前用户已有的 MCP 应用永久 ApiTicket。
     * <p>
     * 返回遮掩后的 ApiTicket（前6位 + ****** + 后6位）。
     * 如果没有有效的 ApiTicket，返回 null。
     *
     * @return 遮掩后的 ApiTicket 或 null
     */
    @ApiOperation("查询已有的永久ApiTicket")
    @GetMapping("/getExistingApiTicket")
    public JsonResult<String> getExistingApiTicket() {
        ThirdPartyApp app = getMcpApp();
        Long uid = SecureUtils.getCurrentUid();

        ThirdPartyAppApiTicket ticketRecord = thirdPartyAppApiTicketService
                .findLatestActivePermanentTicket(app.getId(), uid)
                .orElse(null);
        if (ticketRecord == null || ticketRecord.getApiTicket() == null) {
            return JsonResultImpl.getInstance(null);
        }

        try {
            thirdPartyAppApiTicketService.parseAndValidateApiTicket(ticketRecord.getApiTicket());
            return JsonResultImpl.getInstance(maskApiTicket(ticketRecord.getApiTicket()));
        } catch (JsonException e) {
            log.debug("ApiTicket验证失败，视为无效: {}", e.getMessage());
            return JsonResultImpl.getInstance(null);
        }
    }

    /**
     * 获取 MCP OAuth 应用。
     */
    private ThirdPartyApp getMcpApp() {
        return thirdPartyAppRepo.findByNameIgnoreCase(McpConstant.MCP_OAUTH_APP_NAME)
                .orElseThrow(() -> new JsonException("MCP OAuth应用不存在，请检查系统初始化是否正常"));
    }

    /**
     * 从缓存获取 MCP 应用的 clientSecret。
     */
    private String getClientSecret(Long appId) {
        String cacheKey = McpConstant.MCP_OAUTH_CLIENT_SECRET_CACHE_PREFIX + appId;
        String secret = cacheService.get(cacheKey);
        if (secret == null) {
            throw new JsonException("MCP应用clientSecret未初始化，请检查系统启动日志");
        }
        return secret;
    }

    /**
     * 遮掩 ApiTicket，只保留首尾各6位字符，中间使用6个*代替。
     */
    private String maskApiTicket(String apiTicket) {
        if (apiTicket == null || apiTicket.length() <= 12) {
            return "******";
        }
        return apiTicket.substring(0, 6) + "******" + apiTicket.substring(apiTicket.length() - 6);
    }
}
