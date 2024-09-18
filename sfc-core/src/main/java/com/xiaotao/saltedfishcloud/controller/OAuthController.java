package com.xiaotao.saltedfishcloud.controller;

import com.xiaotao.saltedfishcloud.annotations.AllowAnonymous;
import com.xiaotao.saltedfishcloud.dao.jpa.ThirdPartyAuthPlatformRepo;
import com.xiaotao.saltedfishcloud.dao.redis.TokenService;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.exception.UserNoExistException;
import com.xiaotao.saltedfishcloud.model.ConfigNode;
import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.model.param.BindUserParam;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyAuthPlatform;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyPlatformUser;
import com.xiaotao.saltedfishcloud.model.po.User;
import com.xiaotao.saltedfishcloud.model.vo.UserVO;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyPlatformHandler;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyPlatformManager;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyPlatformUserService;
import com.xiaotao.saltedfishcloud.service.third.model.ThirdPartyPlatformCallbackResult;
import com.xiaotao.saltedfishcloud.service.user.UserService;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import com.xiaotao.saltedfishcloud.validator.annotations.UID;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import javax.annotation.security.RolesAllowed;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Validated
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

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ThirdPartyPlatformUserService thirdPartyPlatformUserService;


    @ApiOperation("使用第三方登录创建新账号")
    @AllowAnonymous
    @GetMapping("createUser")
    @ResponseBody
    public JsonResult<ThirdPartyPlatformCallbackResult> createUser(@RequestParam("actionId") String actionId) {
        UserVO user = thirdPartyPlatformManager.createUser(actionId);
        return JsonResultImpl.getInstance(ThirdPartyPlatformCallbackResult.builder()
                        .newToken(tokenService.generateUserToken(user))
                        .user(user)
                .build());
    }

    @ApiOperation("第三方平台回调接口")
    @GetMapping("/callback/{platformType}")
    @AllowAnonymous
    public ModelAndView callback(@PathVariable("platformType") String platformType, HttpServletRequest request) {
        try {
            ThirdPartyPlatformCallbackResult callbackResult = thirdPartyPlatformManager.doCallback(platformType, request);
            return new ModelAndView("thirdPlatformCallback")
                    .addObject("result", MapperHolder.toJson(callbackResult))
                    .addObject("newToken", callbackResult.getNewToken());
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
        List<String> types = platformList.stream().map(ThirdPartyAuthPlatform::getType).collect(Collectors.toList());
        Map<String, ThirdPartyAuthPlatform> typeMap = thirdPartyAuthPlatformRepo.findByTypeIn(types)
                .stream()
                .collect(Collectors.toMap(ThirdPartyAuthPlatform::getType, Function.identity()));

        // 如果有相同的平台 直接取id赋值上防止重复
        for (ThirdPartyAuthPlatform platform : platformList) {
            Optional.ofNullable(typeMap.get(platform.getType()))
                    .ifPresent(existData -> platform.setId(existData.getId()));
        }

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

    @PostMapping("bindUser")
    @AllowAnonymous
    @ResponseBody
    public JsonResult<UserVO> bindUser(@RequestBody BindUserParam param) {
        UserVO user;
        if (!Boolean.TRUE.equals(param.getAutoBind())) {
            Objects.requireNonNull(param.getAccount(), "用户名不能为空");
            Objects.requireNonNull(param.getPassword(), "密码不能为空");
            User specifyUser = Optional.ofNullable(userService.getUserByAccount(param.getAccount())).orElseThrow(UserNoExistException::new);
            if(!passwordEncoder.encode(param.getPassword()).equals(specifyUser.getPassword())) {
                throw new JsonException("用户名或密码错误");
            }
            user = thirdPartyPlatformManager.bindUser(param.getActionId(), specifyUser);
        } else {
            user = thirdPartyPlatformManager.bindUser(param.getActionId(), null);
        }

        return JsonResultImpl.getInstance(user);
    }

    @GetMapping("listAssocPlatformUser")
    @ApiOperation("列出已关联的第三方平台用户信息")
    @ResponseBody
    public JsonResult<List<ThirdPartyPlatformUser>> listAssocPlatformUser(@RequestParam("uid") @UID Long uid) {
        return JsonResultImpl.getInstance(thirdPartyPlatformUserService.findByUid(uid));
    }
}
