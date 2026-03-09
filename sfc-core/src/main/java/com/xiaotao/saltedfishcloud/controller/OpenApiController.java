package com.xiaotao.saltedfishcloud.controller;

import com.xiaotao.saltedfishcloud.annotations.AllowAnonymous;
import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.model.po.User;
import com.xiaotao.saltedfishcloud.model.vo.UserVO;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyAppTokenService;
import io.swagger.annotations.ApiOperation;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 第三方应用开放平台控制台
 */
@RestController
@RequestMapping("/api/openApi")
public class OpenApiController {
    @Autowired
    private ThirdPartyAppTokenService thirdPartyAppTokenService;

    @ApiOperation("根据用户的授权码获取Access Token")
    @GetMapping("/auth/getAccessToken/v1")
    @AllowAnonymous
    public JsonResult<String> getAccessToken(@RequestParam("code") String code,
                                             @RequestParam("clientSecret") String clientSecret) {
        return JsonResultImpl.getInstance(thirdPartyAppTokenService.getAccessToken(code, clientSecret));
    }

    @ApiOperation("获取开放平台接口凭证 ApiTicket")
    @GetMapping("/auth/getApiTicket/v1")
    @AllowAnonymous
    public JsonResult<String> getApiTicket(
            @RequestParam("appId") Long appId,
            @RequestParam("uid") Long uid,
            @RequestParam("accessToken") String accessToken) {
        return JsonResultImpl.getInstance(thirdPartyAppTokenService.getApiTicket(appId, uid, accessToken));
    }

    @ApiOperation("获取授权的用户信息")
    @GetMapping("/user/profile/v1")
    @RolesAllowed("OAUTH_USER")
    @PreAuthorize("hasAuthority('SCOPE_profile')")
    public JsonResult<UserVO> getUserProfile(@AuthenticationPrincipal User user) {
        UserVO vo = UserVO.from(user, true);
        return JsonResultImpl.getInstance(vo);
    }
}
