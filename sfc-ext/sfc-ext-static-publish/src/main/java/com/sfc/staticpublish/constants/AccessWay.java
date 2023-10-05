package com.sfc.staticpublish.constants;

/**
 * 站点访问方式
 */
public interface AccessWay {
    /**
     * 按主机名匹配。<br>
     * 样例：<br>
     * 用户名: user 站点名称: my-site 站点域名后缀: localhost:9999 <br>
     * 访问地址则为: my-site.localhost:9999
     *
     */
    int BY_HOST = 1;

    /**
     * 按路径匹配
     * 样例：<br>
     * 用户名: user 站点名称: my-site 站点域名后缀: localhost:9999 <br>
     * 访问地址则为: user.localhost:9999/my-site
     */
    int BY_PATH = 2;
}
