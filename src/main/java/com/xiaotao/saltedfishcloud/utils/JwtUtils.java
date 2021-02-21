package com.xiaotao.saltedfishcloud.utils;

import com.xiaotao.saltedfishcloud.exception.HasResultException;
import com.xiaotao.saltedfishcloud.po.User;
import io.jsonwebtoken.*;

import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class JwtUtils {
    private final static int EXPIRATION_TIME = 60*60*24;
    private final static String SECRET = "1145141919810";
    public static final String AUTHORIZATION = "Token";

    public static String generateToken(Object data) {
        Calendar calendar = Calendar.getInstance();
        Date now = calendar.getTime();
        calendar.setTime(now);
        calendar.add(Calendar.SECOND, EXPIRATION_TIME);
        Date expiration = calendar.getTime();

        Map<String, Object> map = new HashMap<>();
        map.put("data", data);
        String res = Jwts.builder().
                setClaims(map)
                .setIssuedAt(now)
                .setExpiration(expiration)
                .signWith(SignatureAlgorithm.HS256, SECRET)
                .compact();
        return res;
    }

    public static Object parse(String token) {
        try {
            Claims body = Jwts.parser()
                    .setSigningKey(SECRET)
                    .parseClaimsJws(token)
                    .getBody();
            return body.get("data");
        } catch (ExpiredJwtException e) {
            throw new HasResultException("token已过期");
        } catch (Exception e) {
            throw new HasResultException("token无效");
        }
    }
}
