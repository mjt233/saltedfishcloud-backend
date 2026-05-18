package com.xiaotao.saltedfishcloud.controller.open;

import com.xiaotao.saltedfishcloud.annotations.AllowAnonymous;
import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyAppTokenService;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 开放平台认证相关接口。
 */
@RestController
@RequestMapping("/api/openApi/auth")
@RequiredArgsConstructor
public class OpenApiAuthController {

    /**
     * 第三方 OAuth Token 业务服务。
     */
    private final ThirdPartyAppTokenService thirdPartyAppTokenService;

    /**
     * 根据用户授权码换取 Access Token。
     *
     * @param code         用户授权码
     * @param clientSecret 第三方应用客户端密钥
     * @return Access Token
     */
    @ApiOperation("根据用户的授权码获取Access Token")
    @GetMapping("/getAccessToken/v1")
    @AllowAnonymous
    public JsonResult<String> getAccessToken(@RequestParam("code") String code,
                                             @RequestParam("clientSecret") String clientSecret) {
        return JsonResultImpl.getInstance(thirdPartyAppTokenService.getAccessToken(code, clientSecret));
    }

    /**
     * 根据 Access Token 换取开放平台接口凭证 ApiTicket。
     *
     * @param accessToken 接口访问授权 Access Token
     * @param permanent   是否申请永久有效的 ApiTicket
     * @return ApiTicket
     */
    @ApiOperation("获取开放平台接口凭证 ApiTicket")
    @GetMapping("/getApiTicket/v1")
    @AllowAnonymous
    public JsonResult<String> getApiTicket(
            @RequestParam("accessToken") String accessToken,
            @RequestParam(value = "permanent", defaultValue = "false") boolean permanent) {
        return JsonResultImpl.getInstance(thirdPartyAppTokenService.getApiTicket(accessToken, permanent, true));
    }
}
