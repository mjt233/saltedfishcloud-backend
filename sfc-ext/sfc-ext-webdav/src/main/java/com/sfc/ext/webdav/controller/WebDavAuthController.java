package com.sfc.ext.webdav.controller;

import com.sfc.ext.webdav.service.WebDavAuthService;
import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.validator.annotations.UID;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/webDavAuth")
@RequiredArgsConstructor
@Validated
public class WebDavAuthController {
    private final WebDavAuthService webDavAuthService;

    @PostMapping("setWebDavPassword")
    public JsonResult<Object> setWebDavPassword(@UID @RequestParam("uid") Long uid,
                                        @RequestParam("password") String password) {
        webDavAuthService.setWebDavPassword(uid, password);
        return JsonResult.emptySuccess();
    }

    @PostMapping("getAuthStatus")
    public JsonResult<Boolean> getAuthStatus(@UID @RequestParam("uid") Long uid) {
        return JsonResultImpl.getInstance(webDavAuthService.existAuth(uid));
    }
}
