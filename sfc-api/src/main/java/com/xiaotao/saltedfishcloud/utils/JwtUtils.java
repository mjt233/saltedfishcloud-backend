package com.xiaotao.saltedfishcloud.utils;

import com.xiaotao.saltedfishcloud.exception.JsonException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class JwtUtils {
    private final static int EXPIRATION_TIME = 60*60*24*31;
    private static String SECRET = "1145141919810";
    public static final String AUTHORIZATION = "Token";

    /**
     * 设置JWT签名密钥。
     * <p>jjwt 0.12+ 要求 HS256 密钥至少为 32 字节（256 bit）。
     * 默认值 "1145141919810"（14 字节）不足以满足该要求，
     * 必须在运行时通过本方法设置一个长度不少于 32 字节的密钥。</p>
     * @param secret 用于JWT签名的密钥字符串
     */
    public static void setSecret(String secret) {
        JwtUtils.SECRET = secret;
    }

    /**
     * 将当前 {@link #SECRET} 转换为符合 jjwt 0.12+ 要求的 {@link SecretKey}。
     * <p>使用 {@link Keys#hmacShaKeyFor(byte[])} 根据密钥字节长度自动选择 HMAC-SHA 算法；
     * 当 {@code SECRET} 的 UTF-8 字节数不少于 32 时采用 HS256。</p>
     * @return 由当前 SECRET 派生的 HMAC-SHA 密钥
     */
    private static SecretKey getKey() {
        return Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成一个包含了data作为负载信息的token
     * @param data  要附加的数据
     * @param expr  token有效时间，单位为秒。小于等于0的值则表示永久有效。
     * @return  token字符串
     */
    public static String generateToken(Object data, int expr) {
        Calendar calendar = Calendar.getInstance();
        Date now = calendar.getTime();
        Date expiration = null;
        if (expr > 0) {
            calendar.setTime(now);
            calendar.add(Calendar.SECOND, expr);
            expiration = calendar.getTime();
        }

        Map<String, Object> map = new HashMap<>();
        map.put("data", data);
        return Jwts.builder()
                .claims(map)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(getKey())
                .compact();
    }

    /**
     * 生成一个包含了data作为负载信息的token，默认有效时间为31天
     * @param data  要附加的数据
     * @return  token字符串
     */
    public static String generateToken(Object data) {
        return generateToken(data, EXPIRATION_TIME);
    }

    /**
     * 检查JWT是否已过期
     * @param token 待检查的token
     * @return  true表示已过期，false表示未过期、仍有效
     */
    public static boolean checkIsExpired(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return false;
        } catch (ExpiredJwtException e) {
            return true;
        }
    }

    /**
     * 解析一个token中的负载数据的json
     * @param token 输入的token
     * @return json字符串
     */
    public static String parse(String token) {
        try {
            Claims body = Jwts.parser()
                    .verifyWith(getKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return (String)body.get("data");
        } catch (ExpiredJwtException e) {
            throw new JsonException("token已过期");
        } catch (Exception e) {
            throw new JsonException("token无效");
        }
    }
}
