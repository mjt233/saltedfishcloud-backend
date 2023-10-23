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

    /**
     * 直接挂载到Web服务根路径下，适用于无法自由使用域名的场景。样例: <br>
     * 用户名:user 站点名称: my-site 服务器ip地址: 192.168.1.100 服务端口: 9999 <br>
     * 访问地址则为: 192.168.1.100:9999/my-site <br>
     * 注意: 该方式由于不依赖主机名区分站点，将会作为优先级最低的无匹配站点下的回调站点。
     */
    int BY_DIRECT_ROOT_PATH = 3;
}
