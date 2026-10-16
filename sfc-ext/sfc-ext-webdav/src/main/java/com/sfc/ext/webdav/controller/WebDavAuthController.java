package com.sfc.ext.webdav.controller;

import com.sfc.ext.webdav.service.WebDavAuthService;
import com.sfc.cloud.model.json.SfcJsonResult;
import com.sfc.cloud.data.validation.SfcUID;
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
    public SfcJsonResult<Object> setWebDavPassword(@SfcUID @RequestParam("uid") Long uid,
                                        @RequestParam("password") String password) {
        webDavAuthService.setWebDavPassword(uid, password);
        return SfcJsonResult.emptySuccess();
    }

    @PostMapping("getAuthStatus")
    public SfcJsonResult<Boolean> getAuthStatus(@SfcUID @RequestParam("uid") Long uid) {
        return SfcJsonResult.getInstance(webDavAuthService.existAuth(uid));
    }
}
