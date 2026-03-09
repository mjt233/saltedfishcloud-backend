package com.xiaotao.saltedfishcloud.controller;

import com.xiaotao.saltedfishcloud.annotations.AllowAnonymous;
import com.xiaotao.saltedfishcloud.constant.SysRole;
import com.xiaotao.saltedfishcloud.dao.jpa.ThirdPartyAuthPlatformRepo;
import com.xiaotao.saltedfishcloud.dao.redis.TokenService;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.exception.UserNoExistException;
import com.xiaotao.saltedfishcloud.model.CommonPageInfo;
import com.xiaotao.saltedfishcloud.model.ConfigNode;
import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.model.param.BindUserParam;
import com.xiaotao.saltedfishcloud.model.param.PageableRequest;
import com.xiaotao.saltedfishcloud.model.po.*;
import com.xiaotao.saltedfishcloud.model.vo.ThirdPartyAppKeyVo;
import com.xiaotao.saltedfishcloud.model.vo.ThirdPartyAppUserAuthorizationVo;
import com.xiaotao.saltedfishcloud.model.vo.UserVO;
import com.xiaotao.saltedfishcloud.service.third.*;
import com.xiaotao.saltedfishcloud.service.third.model.ThirdPartyPlatformCallbackResult;
import com.xiaotao.saltedfishcloud.service.user.UserService;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.xiaotao.saltedfishcloud.validator.annotations.UID;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import jakarta.annotation.security.RolesAllowed;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.UriBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
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

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ThirdPartyPlatformUserService thirdPartyPlatformUserService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ThirdPartyAppService thirdPartyAppService;

    @Autowired
    private ThirdPartyAppKeyService thirdPartyAppKeyService;

    @Autowired
    private ThirdPartyAppAuthorizationService thirdPartyAppAuthorizationService;

    @Autowired
    private ThirdPartyAppTokenService thirdPartyAppTokenService;


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
            String key = "oauth::cb::" + platformType + "::" + Optional.ofNullable(request.getQueryString()).map(SecureUtils::getMd5).orElse("");
            Boolean isSuccess = redisTemplate.opsForValue().setIfAbsent(key, true, Duration.ofSeconds(20));
            if (!Boolean.TRUE.equals(isSuccess)) {
                return new ModelAndView("thirdPlatformCallback")
                        .addObject("error", "不能重复访问");
            }

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
            if (!passwordEncoder.encode(param.getPassword()).equals(specifyUser.getPassword())) {
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

    @ApiOperation("保存/新增一个第三方OAuth应用")
    @PostMapping("saveThirdPartyApp")
    @RolesAllowed(SysRole.ADMIN)
    @ResponseBody
    public JsonResult<ThirdPartyApp> saveThirdPartyApp(@RequestBody @Validated ThirdPartyApp app) {
        if (app.getId() == null && app.getUid() == null) {
            app.setUid(SecureUtils.getCurrentUid());
        }
        thirdPartyAppService.save(app);
        return JsonResultImpl.getInstance(app);
    }

    @ApiOperation("列出系统中的第三方OAuth应用")
    @GetMapping("listThirdPartyApp")
    @RolesAllowed(SysRole.ADMIN)
    @ResponseBody
    public JsonResult<CommonPageInfo<ThirdPartyApp>> listThirdPartyApp(PageableRequest pageableRequest) {
        return JsonResultImpl.getInstance(thirdPartyAppService.listApps(pageableRequest));
    }


    @ApiOperation("删除系统中的第三方OAuth应用")
    @PostMapping("deleteThirdPartyApp")
    @RolesAllowed(SysRole.ADMIN)
    @ResponseBody
    public JsonResult<Object> deleteThirdPartyApp(@RequestBody List<Long> idList) {
        thirdPartyAppService.batchDelete(idList);
        return JsonResult.emptySuccess();
    }

    @ApiOperation("新生成一个第三方OAuth应用密钥")
    @GetMapping("generateNewOauthAppKey")
    @RolesAllowed(SysRole.ADMIN)
    @ResponseBody
    public JsonResult<ThirdPartyAppKeyVo> generateNewOauthAppKey(@RequestParam Long appId, @RequestParam(required = false) String name) {
        return JsonResultImpl.getInstance(thirdPartyAppKeyService.generateNewKey(appId, name));
    }

    @ApiOperation("列出第三方OAuth应用的密钥")
    @GetMapping("listOAuthAppKey")
    @RolesAllowed(SysRole.ADMIN)
    @ResponseBody
    public JsonResult<List<ThirdPartyAppKeyVo>> listOAuthAppKey(@RequestParam Long appId) {
        return JsonResultImpl.getInstance(thirdPartyAppKeyService.listKeyByAppId(appId));
    }

    @ApiOperation("删除第三方OAuth应用密钥")
    @PostMapping("deleteOAuthAppKey")
    @RolesAllowed(SysRole.ADMIN)
    @ResponseBody
    public JsonResult<Object> deleteOAuthAppKey(@RequestBody List<Long> idList) {
        thirdPartyAppKeyService.batchDelete(idList);
        return JsonResult.emptySuccess();
    }

    @ApiOperation("修改第三方OAuth应用密钥信息")
    @PostMapping("changeOAuthAppKey")
    @RolesAllowed(SysRole.ADMIN)
    @ResponseBody
    public JsonResult<Object> changeOAuthAppKey(@RequestBody ThirdPartyAppKeyVo keyVo) {
        thirdPartyAppKeyService.changeKeyInfo(keyVo);
        return JsonResult.emptySuccess();
    }

    @ApiOperation("获取当前用户在第三方OAuth应用的授权信息")
    @GetMapping("getUserAuthorization")
    @ResponseBody
    public JsonResult<ThirdPartyAppUserAuthorizationVo> getUserAuthorization(@RequestParam("appId") Long appId) {
        return JsonResultImpl.getInstance(thirdPartyAppAuthorizationService.getUserAppAuthorization(appId, SecureUtils.getCurrentUid()));
    }

    @ApiOperation("当前用户确认授权第三方应用")
    @GetMapping("authorize")
    @ResponseBody
    public JsonResult<Map<String, String>> authorize(@RequestParam("appId") Long appId,
                                        @RequestParam("scope") String scope) {
        String authorizeCode = thirdPartyAppTokenService.authorize(appId, SecureUtils.getCurrentUid(), scope);
        ThirdPartyApp app = thirdPartyAppService.checkAndGetById(appId);
        String redirectUrl = UriComponentsBuilder.fromHttpUrl(app.getCallbackUrl())
                .queryParam("code", authorizeCode)
                .encode(StandardCharsets.UTF_8)
                .toUriString();

        Map<String, String> data = new HashMap<>();
        data.put("code", authorizeCode);
        data.put("redirectUrl", redirectUrl);
        return JsonResultImpl.getInstance(data);
    }
}
