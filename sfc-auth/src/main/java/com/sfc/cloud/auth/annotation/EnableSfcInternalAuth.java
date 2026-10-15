package com.sfc.cloud.auth.annotation;

import com.sfc.cloud.auth.SfcInternalAuthAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * 启用咸鱼云微服务-服务间内部接口鉴权机制
 * <br>
 * 该注解会向servlet容器自动注入一个过滤器，对访问的url前缀 /api/internal 进行校验必须包含有效的 X-Sfc-Internal-Token
 * <br>
 * 系统需要通过 sys.internal.key 或环境变量 SFC_INTERNAL_KEY 来配置共享密钥
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Import(SfcInternalAuthAutoConfiguration.class)
public @interface EnableSfcInternalAuth {
}
