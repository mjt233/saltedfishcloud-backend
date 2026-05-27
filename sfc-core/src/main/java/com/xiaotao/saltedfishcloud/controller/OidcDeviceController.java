package com.xiaotao.saltedfishcloud.controller;

import com.xiaotao.saltedfishcloud.annotations.AllowAnonymous;
import com.xiaotao.saltedfishcloud.config.oidc.OidcServerProperty;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyApp;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * OIDC 设备授权模式页面控制器。
 * <p>
 * 提供两类浏览器页面：
 * <ul>
 *   <li>匿名可访问的设备激活页：用户在此输入或确认 {@code user_code}</li>
 *   <li>登录后的设备授权确认页：展示客户端名称与请求 scopes，供用户确认授权</li>
 * </ul>
 * </p>
 */
@Controller
@RequiredArgsConstructor
public class OidcDeviceController {

    /**
     * 设备激活页路径。
     */
    public static final String DEVICE_ACTIVATION_PATH = "/oauth/device";

    /**
     * 设备授权确认页路径。
     */
    public static final String DEVICE_CONSENT_PATH = "/oauth2/device-consent";

    private final ThirdPartyAppService thirdPartyAppService;
    private final OidcServerProperty oidcServerProperty;

    /**
     * 渲染设备激活页。
     * <p>
     * 该页面允许匿名访问，用于承接设备授权响应中的 {@code verification_uri}。
     * 用户提交后会跳转到 Spring Authorization Server 的设备核验端点。
     * </p>
     *
     * @param userCode 设备授权响应返回的 {@code user_code}，允许为空
     * @param model    MVC 视图模型
     * @return 设备激活页模板名
     */
    @AllowAnonymous
    @GetMapping(DEVICE_ACTIVATION_PATH)
    public String deviceActivationPage(@RequestParam(name = "user_code", required = false) String userCode,
                                       Model model) {
        model.addAttribute("userCode", Optional.ofNullable(userCode).orElse(""));
        model.addAttribute("verificationEndpoint", oidcServerProperty.getDeviceVerificationEndpoint());
        return "oidcDeviceVerification";
    }

    /**
     * 渲染设备授权确认页。
     *
     * @param clientId 设备授权所属客户端 ID
     * @param state    设备核验过程中的状态参数
     * @param userCode 设备授权返回的 {@code user_code}
     * @param scope    空格分隔的 scope 列表
     * @param model    MVC 视图模型
     * @return 设备授权确认页模板名
     */
    @GetMapping(DEVICE_CONSENT_PATH)
    public String deviceConsentPage(@RequestParam("client_id") String clientId,
                                    @RequestParam("state") String state,
                                    @RequestParam("user_code") String userCode,
                                    @RequestParam(name = "scope", required = false) String scope,
                                    Model model) {
        model.addAttribute("clientId", clientId);
        model.addAttribute("clientName", resolveClientName(clientId));
        model.addAttribute("state", state);
        model.addAttribute("userCode", userCode);
        model.addAttribute("verificationEndpoint", oidcServerProperty.getDeviceVerificationEndpoint());
        model.addAttribute("scopes", splitScopes(scope));
        return "oidcDeviceConsent";
    }

    /**
     * 根据客户端 ID 解析展示名称。
     *
     * @param clientId 客户端 ID
     * @return 应用名称；若无法解析则回退为原始 clientId
     */
    private String resolveClientName(String clientId) {
        try {
            return Optional.ofNullable(thirdPartyAppService.findById(Long.valueOf(clientId)))
                    .map(ThirdPartyApp::getName)
                    .filter(name -> name != null && !name.isBlank())
                    .orElse(clientId);
        } catch (NumberFormatException ex) {
            return clientId;
        }
    }

    /**
     * 将空格分隔的 scope 字符串拆分为列表。
     *
     * @param scope 空格分隔的 scope 字符串
     * @return scope 列表；为空时返回空列表
     */
    private List<String> splitScopes(String scope) {
        if (scope == null || scope.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(scope.trim().split("\\s+")).toList();
    }
}
