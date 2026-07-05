package com.xiaotao.saltedfishcloud.controller.open;

import com.xiaotao.saltedfishcloud.constant.StandardScopes;
import com.xiaotao.saltedfishcloud.constant.SysRole;
import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.model.po.UserPrincipal;
import com.xiaotao.saltedfishcloud.model.vo.OpenUserVo;
import io.swagger.annotations.ApiOperation;
import jakarta.annotation.security.RolesAllowed;
import jakarta.servlet.http.HttpServletRequest;
import com.xiaotao.saltedfishcloud.annotations.RequireScope;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RolesAllowed(SysRole.OAUTH_USER)
@RequestMapping("/api/openApi/user")
public class OpenApiUserController {

    @ApiOperation("获取授权的用户信息")
    @GetMapping("/profile/v1")
    @RequireScope(StandardScopes.PROFILE)
    public JsonResult<OpenUserVo> getUserProfile(@AuthenticationPrincipal UserPrincipal user, HttpServletRequest request) {
        OpenUserVo vo = OpenUserVo.of(user);
        vo.setAvatar(UriComponentsBuilder.fromHttpUrl(request.getRequestURL().toString())
                        .replacePath("/api/user/avatar/" + user.getUsername())
                        .replaceQueryParam("uid", user.getId())
                        .build()
                        .toString());
        return JsonResultImpl.getInstance(vo);
    }
}
