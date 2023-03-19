package com.sfc.rpc.annotation;

import com.sfc.rpc.config.RpcAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Import(RpcAutoConfiguration.class)
public @interface EnableRpc {
}
