package com.xiaotao.saltedfishcloud.common;

import org.springframework.core.io.Resource;

/**
 * 支持通过URL重定向的资源，响应body中的对象实现该接口时，会引导客户端转跳到指定URL
 */
public interface RedirectableResource extends RedirectableUrl,Resource {
}
