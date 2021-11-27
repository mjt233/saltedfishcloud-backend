package com.xiaotao.saltedfishcloud.service.node.cache.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * 移除节点缓存
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RemoveNodeCache {
    /**
     * 用户ID参数位置
     */
    int uid();

    /**
     * 被移除的节点ID参数位置
     */
    int nid();


}
