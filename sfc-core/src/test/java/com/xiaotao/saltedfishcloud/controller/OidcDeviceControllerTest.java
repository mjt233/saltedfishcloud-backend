package com.xiaotao.saltedfishcloud.controller;

import com.xiaotao.saltedfishcloud.config.oidc.OidcServerProperty;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyApp;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyAppService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * {@link OidcDeviceController} 的控制器测试。
 * <p>
 * 仅验证设备授权模式所需的两个服务端页面：
 * <ul>
 *   <li>匿名可访问的设备激活页</li>
 *   <li>已认证后展示 scope 明细的设备授权确认页</li>
 * </ul>
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class OidcDeviceControllerTest {

    @Mock
    private ThirdPartyAppService thirdPartyAppService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        OidcServerProperty property = new OidcServerProperty();
        mockMvc = MockMvcBuilders.standaloneSetup(new OidcDeviceController(thirdPartyAppService, property)).build();
    }

    /**
     * 验证设备激活页支持匿名访问，并将默认 user_code 与核验端点地址渲染到模型中。
     */
    @Test
    void deviceActivationPageShouldRenderVerificationForm() throws Exception {
        mockMvc.perform(get("/oauth/device").param("user_code", "ABCD-EFGH"))
                .andExpect(status().isOk())
                .andExpect(view().name("oidcDeviceVerification"))
                .andExpect(model().attribute("userCode", "ABCD-EFGH"))
                .andExpect(model().attribute("verificationEndpoint", "/oauth2/device_verification"));
    }

    /**
     * 验证设备授权确认页会渲染客户端名称、user_code 与请求 scopes，供用户确认授权。
     */
    @Test
    void deviceConsentPageShouldRenderClientAndScopes() throws Exception {
        ThirdPartyApp app = new ThirdPartyApp();
        app.setName("示例客户端");
        when(thirdPartyAppService.findById(42L)).thenReturn(app);

        mockMvc.perform(get("/oauth2/device-consent")
                        .param("client_id", "42")
                        .param("scope", "openid profile")
                        .param("state", "state-1")
                        .param("user_code", "ABCD-EFGH"))
                .andExpect(status().isOk())
                .andExpect(view().name("oidcDeviceConsent"))
                .andExpect(model().attribute("clientId", "42"))
                .andExpect(model().attribute("clientName", "示例客户端"))
                .andExpect(model().attribute("state", "state-1"))
                .andExpect(model().attribute("userCode", "ABCD-EFGH"))
                .andExpect(model().attribute("verificationEndpoint", "/oauth2/device_verification"))
                .andExpect(model().attribute("scopes", java.util.List.of("openid", "profile")));
    }
}
