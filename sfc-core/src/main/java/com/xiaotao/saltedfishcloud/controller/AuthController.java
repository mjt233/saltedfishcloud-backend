package com.xiaotao.saltedfishcloud.controller;

import com.xiaotao.saltedfishcloud.annotations.AllowAnonymous;
import com.xiaotao.saltedfishcloud.dao.redis.TokenService;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyPlatformManager;
import com.xiaotao.saltedfishcloud.service.third.model.ThirdPartyPlatformCallbackResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;


@Controller
@RequestMapping("/auth")
public class AuthController {
    @Autowired
    private ThirdPartyPlatformManager thirdPartyPlatformManager;

    @Autowired
    private TokenService tokenService;

    @GetMapping("/callback/{platformType}")
    @AllowAnonymous
    public ModelAndView callback(@PathVariable("platformType") String platformType, HttpServletRequest request) {
        ThirdPartyPlatformCallbackResult callbackResult = thirdPartyPlatformManager.doCallback(platformType, request);
        return new ModelAndView("thirdPlatformCallback")
                .addObject("result", callbackResult)
                .addObject("newToken", callbackResult.getIsNewUser() ? tokenService.generateUserToken(callbackResult.getUser()) : null);
    }
}
