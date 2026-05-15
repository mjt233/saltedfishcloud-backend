package com.xiaotao.saltedfishcloud.controller.open;

import com.xiaotao.saltedfishcloud.annotations.AllowAnonymous;
import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyAppTokenService;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/openApi/auth")
public class OpenApiAuthController {
    @Autowired
    private ThirdPartyAppTokenService thirdPartyAppTokenService;

    @ApiOperation("根据用户的授权码获取Access Token")
    @GetMapping("/getAccessToken/v1")
    @AllowAnonymous
    public JsonResult<String> getAccessToken(@RequestParam("code") String code,
                                             @RequestParam("clientSecret") String clientSecret) {
        return JsonResultImpl.getInstance(thirdPartyAppTokenService.getAccessToken(code, clientSecret));
    }

    @ApiOperation("获取开放平台接口凭证 ApiTicket")
    @GetMapping("/getApiTicket/v1")
    @AllowAnonymous
    public JsonResult<String> getApiTicket(
            @RequestParam("appId") Long appId,
            @RequestParam("uid") Long uid,
            @RequestParam("accessToken") String accessToken,
            @RequestParam(value = "permanent", defaultValue = "false") boolean permanent) {
        return JsonResultImpl.getInstance(thirdPartyAppTokenService.getApiTicket(appId, uid, accessToken, permanent));
    }
}
