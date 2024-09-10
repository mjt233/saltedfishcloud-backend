package com.xiaotao.saltedfishcloud.controller;

import com.xiaotao.saltedfishcloud.annotations.AllowAnonymous;
import com.xiaotao.saltedfishcloud.dao.jpa.ThirdPartyAuthPlatformRepo;
import com.xiaotao.saltedfishcloud.dao.redis.TokenService;
import com.xiaotao.saltedfishcloud.model.ConfigNode;
import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyAuthPlatform;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyPlatformHandler;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyPlatformManager;
import com.xiaotao.saltedfishcloud.service.third.model.ThirdPartyPlatformCallbackResult;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import javax.annotation.security.RolesAllowed;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;


@Controller
@RequestMapping("/api/oauth")
@Slf4j
@Api(value = "OAuth相关控制器")
public class OAuthController {
    @Autowired
    private ThirdPartyPlatformManager thirdPartyPlatformManager;

    @Autowired
    private ThirdPartyAuthPlatformRepo thirdPartyAuthPlatformRepo;

    @Autowired
    private TokenService tokenService;

    @ApiOperation("第三方平台回调接口")
    @GetMapping("/callback/{platformType}")
    @AllowAnonymous
    public ModelAndView callback(@PathVariable("platformType") String platformType, HttpServletRequest request) {
        try {
            ThirdPartyPlatformCallbackResult callbackResult = thirdPartyPlatformManager.doCallback(platformType, request);
            String newToken = callbackResult.getUser() != null ? tokenService.generateUserToken(callbackResult.getUser()) : null;
            return new ModelAndView("thirdPlatformCallback")
                    .addObject("result", callbackResult)
                    .addObject("newToken", newToken);
        } catch (Exception e) {
            log.error(platformType + "平台第三方登录回调出错", e);
            return new ModelAndView("thirdPlatformCallback")
                    .addObject("error", e.getMessage());
        }
    }

    @ApiOperation("管理员接口-获取第三方平台配置节点，用于构建表单视图")
    @GetMapping("getThirdPartyPlatformConfig")
    @ResponseBody
    @RolesAllowed("ADMIN")
    public JsonResult<Map<String, List<ConfigNode>>> getThirdPartyPlatformConfig() {
        return JsonResultImpl.getInstance(
                thirdPartyPlatformManager.getAllPlatformHandler().stream()
                        .collect(Collectors.toMap(
                                ThirdPartyPlatformHandler::getType,
                                ThirdPartyPlatformHandler::getPlatformConfigNode))
        );
    }

    @ApiOperation("管理员接口-获取第三方平台配置参数值")
    @GetMapping("getThirdPartyPlatformConfigValue")
    @ResponseBody
    @RolesAllowed("ADMIN")
    public JsonResult<Map<String, ThirdPartyAuthPlatform>> getThirdPartyPlatformConfigValue() {
        return JsonResultImpl.getInstance(
                thirdPartyPlatformManager.listPlatform()
                        .stream()
                        .collect(Collectors.toMap(ThirdPartyAuthPlatform::getType, Function.identity()))
        );
    }

    @ApiOperation("管理员接口-保存第三方平台配置参数值")
    @PostMapping("saveThirdPartyPlatformConfigValue")
    @ResponseBody
    @RolesAllowed("ADMIN")
    public JsonResult<?> saveThirdPartyPlatformConfigValue(@RequestBody List<ThirdPartyAuthPlatform> platformList) {
        return JsonResultImpl.getInstance(
                thirdPartyAuthPlatformRepo.saveAll(platformList)
        );
    }


    /**
     * 列出系统当前可用的第三方平台（第三方平台已注册处理器）
     */
    @GetMapping("listPlatform")
    @AllowAnonymous
    @ResponseBody
    public JsonResult<List<ThirdPartyAuthPlatform>> listPlatform() {
        List<ThirdPartyAuthPlatform> list = thirdPartyPlatformManager.listPlatform();
        for (ThirdPartyAuthPlatform platform : list) {
            platform.setConfig(null);
        }
        return JsonResultImpl.getInstance(list);
    }
}
