package com.sfc.cloud.utils;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sfc.cloud.model.UserPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 用户会话凭证工具：从请求中取出凭证，并用与 core 相同的判定口径本地验签。
 */
@UtilityClass
@Slf4j
public class SecureUtils {
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS, true)
            .build();

    /**
     * 从请求中获取token字符串
     *
     * @param request 请求对象
     * @return 凭证原值；三个入口都没有时返回 {@code null}
     */
    public static String getToken(ServerHttpRequest request) {
        String token = request.getHeaders().getFirst("Token");
        if (token == null) {
            token = request.getQueryParams().getFirst("Token");
        }
        if (token == null) {
            List<HttpCookie> cookies = request.getCookies().get("token");
            // core 遍历同名 Cookie 时是"后来的覆盖先前的"（不 break），这里取最后一个以保持同一结果
            if (cookies != null && !cookies.isEmpty()) {
                token = cookies.getLast().getValue();
            }
        }
        return StringUtils.hasText(token) ? token : null;
    }


    private static SecretKey getKey(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 获取并验证用户身份令牌token，返回用户信息对象
     * @param token 用户原始token
     * @param secret    验证签名用的私钥
     * @return  用户信息对象
     */
    public static UserPrincipal getAndValidUserToken(String token, String secret) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getKey(secret))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String userPrincipalJson = claims.get("data").toString();
            return OBJECT_MAPPER.readValue(userPrincipalJson, UserPrincipal.class);
        } catch (ExpiredJwtException e) {
            log.debug("[凭证] 已过期：{}", e.getMessage());
            return null;
        } catch (Exception e) {
            // 签名不符、结构损坏、载荷非法等一律视为"无效凭证"：对外语义与 core 的
            // JwtAuthenticationFilter 一致（它同样吞掉异常并继续以匿名身份放行，最终由鉴权层给 403）
            log.debug("[凭证] 验签失败：{}", e.toString());
            return null;
        }
    }
}
