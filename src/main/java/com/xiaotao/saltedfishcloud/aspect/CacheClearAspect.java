package com.xiaotao.saltedfishcloud.aspect;

import com.xiaotao.saltedfishcloud.annotations.ClearCachePath;
import com.xiaotao.saltedfishcloud.config.RedisConfig;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.LocalVariableTableParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.lang.reflect.Method;
import java.util.Set;

@Aspect
@Component
@Slf4j
public class CacheClearAspect {
    @Resource
    private RedisConfig redisConfig;


    @AfterReturning("@annotation(com.xiaotao.saltedfishcloud.annotations.ClearCachePath)")
    public void clearCache(JoinPoint point) {
        Method method = ((MethodSignature) point.getSignature()).getMethod();
        ClearCachePath cacheRemove = method.getAnnotation(ClearCachePath.class);
        String[] keys = cacheRemove.value();
        for (String key : keys) {
            if (key.contains("#"))
                key = parseKey(key, method, point.getArgs());
            key = key.replaceAll("\\\\+|/+", "/");
            Set<String> deleteKeys = redisConfig.getRedisTemplate().keys(key + "/*");
            deleteKeys.add(key);
            redisConfig.getRedisTemplate().delete(deleteKeys);
            log.debug("cache key: " + key + " deleted");
        }
    }

    /**
     * 解析spEL表达式
     */
    private String parseKey(String key, Method method, Object [] args){
        LocalVariableTableParameterNameDiscoverer u =
                new LocalVariableTableParameterNameDiscoverer();
        String[] paraNameArr = u.getParameterNames(method);
        ExpressionParser parser = new SpelExpressionParser();
        StandardEvaluationContext context = new StandardEvaluationContext();

        for (int i = 0; i < paraNameArr.length; i++) {
            context.setVariable(paraNameArr[i], args[i]);
        }
        return parser.parseExpression(key).getValue(context, String.class);
    }
}
