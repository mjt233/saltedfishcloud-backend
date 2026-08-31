package com.xiaotao.saltedfishcloud.config.security.oidc;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 将 public client（{@code token_endpoint_auth_method=none}）在设备授权相关请求上的
 * 表单 {@code client_id} 转换为 {@link OAuth2ClientAuthenticationToken}。
 * <p>
 * Spring Authorization Server 内置的 {@code PublicClientAuthenticationConverter}
 * 仅匹配「授权码 + PKCE 换票」请求（{@code grant_type=authorization_code} 且带
 * {@code code}/{@code code_verifier}），不覆盖：
 * </p>
 * <ul>
 *   <li>{@code POST /oauth2/device_authorization}（申请设备码，通常无 grant_type）</li>
 *   <li>{@code POST /oauth2/token} 且 {@code grant_type=urn:ietf:params:oauth:grant-type:device_code}</li>
 *   <li>{@code POST /oauth2/token} 且 {@code grant_type=refresh_token}（public client 刷新）</li>
 * </ul>
 * <p>
 * 上述请求若无 converter 命中，客户端认证过滤器会直接返回空 body 的 HTTP 401。
 * 本转换器在无 {@code client_secret}、无 Basic 凭证的前提下识别这些请求。
 * </p>
 *
 * @see PublicClientDeviceAuthenticationProvider
 * @see <a href="https://github.com/spring-projects/spring-authorization-server/issues/1522">SAS #1522</a>
 */
public final class PublicClientDeviceAuthenticationConverter implements AuthenticationConverter {

    /**
     * 设备授权换票的标准 grant_type 值（RFC 8628）。
     */
    private static final String DEVICE_CODE_GRANT_TYPE =
            AuthorizationGrantType.DEVICE_CODE.getValue();

    /**
     * 从请求中提取 public client 设备流相关的客户端认证令牌；不匹配时返回 {@code null}。
     *
     * @param request 当前 HTTP 请求
     * @return 未认证的 {@link OAuth2ClientAuthenticationToken}，或 {@code null}
     */
    @Override
    public Authentication convert(HttpServletRequest request) {
        // 仅处理 POST 表单类客户端认证请求
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return null;
        }

        // 已携带 client_secret 或 Basic 时交给 confidential 转换器
        if (StringUtils.hasText(request.getParameter(OAuth2ParameterNames.CLIENT_SECRET))) {
            return null;
        }
        String authorization = request.getHeader("Authorization");
        if (StringUtils.hasText(authorization) && authorization.regionMatches(true, 0, "Basic ", 0, 6)) {
            return null;
        }

        // 授权码 + PKCE 仍由框架 PublicClientAuthenticationConverter 处理
        String grantType = request.getParameter(OAuth2ParameterNames.GRANT_TYPE);
        if (AuthorizationGrantType.AUTHORIZATION_CODE.getValue().equals(grantType)) {
            return null;
        }

        // 匹配：无 grant_type（设备码申请）或 device_code / refresh_token 换票
        if (StringUtils.hasText(grantType)
                && !DEVICE_CODE_GRANT_TYPE.equals(grantType)
                && !AuthorizationGrantType.REFRESH_TOKEN.getValue().equals(grantType)) {
            return null;
        }

        MultiValueMap<String, String> parameters = getFormParameters(request);
        String clientId = parameters.getFirst(OAuth2ParameterNames.CLIENT_ID);
        List<String> clientIds = parameters.get(OAuth2ParameterNames.CLIENT_ID);
        if (!StringUtils.hasText(clientId) || clientIds == null || clientIds.size() != 1) {
            // 本转换器已判定为 public device/refresh 场景但 client_id 非法时直接失败，
            // 避免后续 converter 全部 miss 后返回空 401
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_REQUEST);
        }

        // 附加参数供 provider 与后续 grant 使用（去掉 client_id）
        Map<String, Object> additionalParameters = new HashMap<>();
        parameters.forEach((key, values) -> {
            if (OAuth2ParameterNames.CLIENT_ID.equals(key) || values == null || values.isEmpty()) {
                return;
            }
            additionalParameters.put(key, values.size() == 1 ? values.get(0) : values.toArray(new String[0]));
        });

        return new OAuth2ClientAuthenticationToken(
                clientId,
                ClientAuthenticationMethod.NONE,
                null,
                additionalParameters
        );
    }

    /**
     * 读取 application/x-www-form-urlencoded 表单参数（不含 query string）。
     *
     * @param request HTTP 请求
     * @return 表单参数映射
     */
    private static MultiValueMap<String, String> getFormParameters(HttpServletRequest request) {
        Map<String, String[]> parameterMap = request.getParameterMap();
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameterMap.forEach((key, values) -> {
            // 设备/令牌端点的客户端标识与凭证应来自表单 body；
            // Servlet 会把 query 与 form 合并，这里仍按参数名收集，与 SAS 内置 converter 行为一致
            if (values != null) {
                for (String value : values) {
                    parameters.add(key, value);
                }
            }
        });
        return parameters;
    }
}
