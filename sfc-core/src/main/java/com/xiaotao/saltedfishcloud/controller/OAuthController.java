package com.xiaotao.saltedfishcloud.controller;

import com.xiaotao.saltedfishcloud.annotations.AllowAnonymous;
import com.xiaotao.saltedfishcloud.cache.CacheKeyPrefixes;
import com.xiaotao.saltedfishcloud.cache.CacheService;
import com.sfc.cloud.constant.SfcSysRole;
import com.xiaotao.saltedfishcloud.dao.jpa.ThirdPartyAuthPlatformRepo;
import com.xiaotao.saltedfishcloud.dao.redis.TokenService;
import com.sfc.cloud.exception.SfcJsonException;
import com.xiaotao.saltedfishcloud.exception.UserNoExistException;
import com.xiaotao.saltedfishcloud.ext.oidc.OidcScopeInfo;
import com.xiaotao.saltedfishcloud.ext.oidc.OidcScopeModule;
import com.sfc.cloud.model.SfcPageInfo;
import com.xiaotao.saltedfishcloud.model.ConfigNode;
import com.sfc.cloud.model.json.SfcJsonResult;
import com.xiaotao.saltedfishcloud.model.param.BindUserParam;
import com.sfc.cloud.model.param.SfcPageableRequest;
import com.xiaotao.saltedfishcloud.model.po.*;
import com.xiaotao.saltedfishcloud.model.vo.ThirdPartyAppKeyVo;
import com.xiaotao.saltedfishcloud.model.vo.ThirdPartyAppUserAuthorizationVo;
import com.xiaotao.saltedfishcloud.model.vo.UserVO;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyAppAuthorizationService;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyAppKeyService;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyAppService;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyPlatformHandler;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyPlatformManager;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyPlatformUserService;
import com.xiaotao.saltedfishcloud.service.third.model.ThirdPartyPlatformCallbackResult;
import com.xiaotao.saltedfishcloud.service.user.UserService;
import com.sfc.cloud.utils.SfcMapperHolder;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.sfc.cloud.data.validation.SfcUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;


@Controller
@RequestMapping("/api/oauth")
@Slf4j
@Tag(name = "OAuth相关控制器")
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
    private CacheService cacheService;

    @Autowired
    private ThirdPartyAppService thirdPartyAppService;

    @Autowired
    private ThirdPartyAppKeyService thirdPartyAppKeyService;

    @Autowired
    private ThirdPartyAppAuthorizationService thirdPartyAppAuthorizationService;

    @Autowired
    private List<OidcScopeModule> scopeModules;

    /**
     * 获取系统中所有已注册的 OIDC scope 模块及其 scope 列表。
     *
     * @return 模块列表，每个模块包含模块信息及其下属的所有 scope
     */
    @GetMapping("listModules")
    @ResponseBody
    public SfcJsonResult<List<OidcScopeModuleVO>> listModules() {
        List<OidcScopeModuleVO> modules = scopeModules.stream()
                .map(m -> new OidcScopeModuleVO(
                        m.getModuleId(),
                        m.getModuleName(),
                        m.getDescription(),
                        m.getIcon(),
                        m.getScopes()))
                .toList();
        return SfcJsonResult.getInstance(modules);
    }

    /**
     * OIDC scope 模块视图对象
     *
     * @param moduleId    模块唯一标识
     * @param moduleName  模块显示名称
     * @param description 模块描述
     * @param icon        模块图标
     * @param scopes      该模块提供的所有 scope
     */
    public record OidcScopeModuleVO(String moduleId, String moduleName, String description, String icon,
                                    List<OidcScopeInfo> scopes) {
    }

    @Operation(summary = "使用第三方登录创建新账号")
    @AllowAnonymous
    @GetMapping("createUser")
    @ResponseBody
    public SfcJsonResult<ThirdPartyPlatformCallbackResult> createUser(@RequestParam("actionId") String actionId) {
        UserVO user = thirdPartyPlatformManager.createUser(actionId);
        return SfcJsonResult.getInstance(ThirdPartyPlatformCallbackResult.builder()
                .newToken(tokenService.generateUserToken(user))
                .user(user)
                .build());
    }

    @Operation(summary = "第三方平台回调接口")
    @GetMapping("/callback/{platformType}")
    @AllowAnonymous
    public ModelAndView callback(@PathVariable("platformType") String platformType, HttpServletRequest request) {
        try {
            String key = CacheKeyPrefixes.OAUTH_CALLBACK + platformType + "::" + Optional.ofNullable(request.getQueryString()).map(SecureUtils::getMd5).orElse("");
            boolean isSuccess = cacheService.setIfAbsent(key, true, 20, TimeUnit.SECONDS);
            if (!isSuccess) {
                return new ModelAndView("thirdPlatformCallback")
                        .addObject("error", "不能重复访问");
            }

            ThirdPartyPlatformCallbackResult callbackResult = thirdPartyPlatformManager.doCallback(platformType, request);
            return new ModelAndView("thirdPlatformCallback")
                    .addObject("result", SfcMapperHolder.toJson(callbackResult))
                    .addObject("newToken", callbackResult.getNewToken());
        } catch (Exception e) {
            log.error(platformType + "平台第三方登录回调出错", e);
            return new ModelAndView("thirdPlatformCallback")
                    .addObject("error", e.getMessage());
        }
    }

    @Operation(summary = "管理员接口-获取第三方平台配置节点，用于构建表单视图")
    @GetMapping("getThirdPartyPlatformConfig")
    @ResponseBody
    @RolesAllowed("ADMIN")
    public SfcJsonResult<Map<String, List<ConfigNode>>> getThirdPartyPlatformConfig() {
        return SfcJsonResult.getInstance(
                thirdPartyPlatformManager.getAllPlatformHandler().stream()
                        .collect(Collectors.toMap(
                                ThirdPartyPlatformHandler::getType,
                                ThirdPartyPlatformHandler::getPlatformConfigNode))
        );
    }

    @Operation(summary = "管理员接口-获取第三方平台配置参数值")
    @GetMapping("getThirdPartyPlatformConfigValue")
    @ResponseBody
    @RolesAllowed("ADMIN")
    public SfcJsonResult<Map<String, ThirdPartyAuthPlatform>> getThirdPartyPlatformConfigValue() {
        return SfcJsonResult.getInstance(
                thirdPartyPlatformManager.listPlatform()
                        .stream()
                        .collect(Collectors.toMap(ThirdPartyAuthPlatform::getType, Function.identity()))
        );
    }

    @Operation(summary = "管理员接口-保存第三方平台配置参数值")
    @PostMapping("saveThirdPartyPlatformConfigValue")
    @ResponseBody
    @RolesAllowed("ADMIN")
    public SfcJsonResult<?> saveThirdPartyPlatformConfigValue(@RequestBody List<ThirdPartyAuthPlatform> platformList) {
        List<String> types = platformList.stream().map(ThirdPartyAuthPlatform::getType).collect(Collectors.toList());
        Map<String, ThirdPartyAuthPlatform> typeMap = thirdPartyAuthPlatformRepo.findByTypeIn(types)
                .stream()
                .collect(Collectors.toMap(ThirdPartyAuthPlatform::getType, Function.identity()));

        // 如果有相同的平台 直接取id赋值上防止重复
        for (ThirdPartyAuthPlatform platform : platformList) {
            Optional.ofNullable(typeMap.get(platform.getType()))
                    .ifPresent(existData -> platform.setId(existData.getId()));
        }

        return SfcJsonResult.getInstance(
                thirdPartyAuthPlatformRepo.saveAll(platformList)
        );
    }


    /**
     * 列出系统当前可用的第三方平台（第三方平台已注册处理器）
     */
    @GetMapping("listPlatform")
    @AllowAnonymous
    @ResponseBody
    public SfcJsonResult<List<ThirdPartyAuthPlatform>> listPlatform() {
        List<ThirdPartyAuthPlatform> list = thirdPartyPlatformManager.listPlatform();
        for (ThirdPartyAuthPlatform platform : list) {
            platform.setConfig(null);
        }
        return SfcJsonResult.getInstance(list);
    }

    @PostMapping("bindUser")
    @AllowAnonymous
    @ResponseBody
    public SfcJsonResult<UserVO> bindUser(@RequestBody BindUserParam param) {
        UserVO user;
        if (!Boolean.TRUE.equals(param.getAutoBind())) {
            Objects.requireNonNull(param.getAccount(), "用户名不能为空");
            Objects.requireNonNull(param.getPassword(), "密码不能为空");
            User specifyUser = Optional.ofNullable(userService.getUserByAccount(param.getAccount())).orElseThrow(UserNoExistException::new);
            if (!passwordEncoder.encode(param.getPassword()).equals(specifyUser.getPwd())) {
                throw new SfcJsonException("用户名或密码错误");
            }
            user = thirdPartyPlatformManager.bindUser(param.getActionId(), specifyUser);
        } else {
            user = thirdPartyPlatformManager.bindUser(param.getActionId(), null);
        }

        return SfcJsonResult.getInstance(user);
    }

    @GetMapping("listAssocPlatformUser")
    @Operation(summary = "列出已关联的第三方平台用户信息")
    @ResponseBody
    public SfcJsonResult<List<ThirdPartyPlatformUser>> listAssocPlatformUser(@RequestParam("uid") @SfcUID Long uid) {
        return SfcJsonResult.getInstance(thirdPartyPlatformUserService.findByUid(uid));
    }

    @Operation(summary = "保存/新增一个第三方OAuth应用")
    @PostMapping("saveThirdPartyApp")
    @RolesAllowed(SfcSysRole.ADMIN)
    @ResponseBody
    public SfcJsonResult<ThirdPartyApp> saveThirdPartyApp(@RequestBody @Validated ThirdPartyApp app) {
        if (app.getId() == null && app.getUid() == null) {
            app.setUid(SecureUtils.getCurrentUid());
        }
        thirdPartyAppService.save(app);
        return SfcJsonResult.getInstance(app);
    }

    @Operation(summary = "列出系统中的第三方OAuth应用")
    @GetMapping("listThirdPartyApp")
    @RolesAllowed(SfcSysRole.ADMIN)
    @ResponseBody
    public SfcJsonResult<SfcPageInfo<ThirdPartyApp>> listThirdPartyApp(SfcPageableRequest pageableRequest) {
        return SfcJsonResult.getInstance(thirdPartyAppService.listApps(pageableRequest));
    }


    @Operation(summary = "删除系统中的第三方OAuth应用")
    @PostMapping("deleteThirdPartyApp")
    @RolesAllowed(SfcSysRole.ADMIN)
    @ResponseBody
    public SfcJsonResult<Object> deleteThirdPartyApp(@RequestBody List<Long> idList) {
        thirdPartyAppService.batchDelete(idList);
        return SfcJsonResult.emptySuccess();
    }

    @Operation(summary = "新生成一个第三方OAuth应用密钥")
    @GetMapping("generateNewOauthAppKey")
    @RolesAllowed(SfcSysRole.ADMIN)
    @ResponseBody
    public SfcJsonResult<ThirdPartyAppKeyVo> generateNewOauthAppKey(@RequestParam Long appId, @RequestParam(required = false) String name) {
        return SfcJsonResult.getInstance(thirdPartyAppKeyService.generateNewKey(appId, name));
    }

    @Operation(summary = "列出第三方OAuth应用的密钥")
    @GetMapping("listOAuthAppKey")
    @RolesAllowed(SfcSysRole.ADMIN)
    @ResponseBody
    public SfcJsonResult<List<ThirdPartyAppKeyVo>> listOAuthAppKey(@RequestParam Long appId) {
        return SfcJsonResult.getInstance(thirdPartyAppKeyService.listKeyByAppId(appId));
    }

    @Operation(summary = "删除第三方OAuth应用密钥")
    @PostMapping("deleteOAuthAppKey")
    @RolesAllowed(SfcSysRole.ADMIN)
    @ResponseBody
    public SfcJsonResult<Object> deleteOAuthAppKey(@RequestBody List<Long> idList) {
        thirdPartyAppKeyService.batchDelete(idList);
        return SfcJsonResult.emptySuccess();
    }

    @Operation(summary = "修改第三方OAuth应用密钥信息")
    @PostMapping("changeOAuthAppKey")
    @RolesAllowed(SfcSysRole.ADMIN)
    @ResponseBody
    public SfcJsonResult<Object> changeOAuthAppKey(@RequestBody ThirdPartyAppKeyVo keyVo) {
        thirdPartyAppKeyService.changeKeyInfo(keyVo);
        return SfcJsonResult.emptySuccess();
    }

    @Operation(summary = "获取当前用户在第三方OAuth应用的授权信息")
    @GetMapping("getUserAuthorization")
    @ResponseBody
    public SfcJsonResult<ThirdPartyAppUserAuthorizationVo> getUserAuthorization(@RequestParam("appId") Long appId) {
        return SfcJsonResult.getInstance(thirdPartyAppAuthorizationService.getUserAppAuthorization(appId, SecureUtils.getCurrentUid()));
    }

    @Operation(summary = "列出用户所有已关联的第三方OAuth应用授权")
    @GetMapping("listUserAuthentication")
    @ResponseBody
    public SfcJsonResult<List<ThirdPartyAppAuthorization>> listUserAuthentication(@RequestParam("uid") @SfcUID Long uid) {
        return SfcJsonResult.getInstance(thirdPartyAppAuthorizationService.findByUid(uid));
    }

    @Operation(summary = "撤销第三方OAuth应用授权")
    @PostMapping("revoke")
    @ResponseBody
    public SfcJsonResult<Object> revoke(@RequestParam("appId") Long appId, @RequestParam("uid") @SfcUID Long uid) {
        thirdPartyAppAuthorizationService.revoke(appId, uid);
        return SfcJsonResult.emptySuccess();
    }
}
