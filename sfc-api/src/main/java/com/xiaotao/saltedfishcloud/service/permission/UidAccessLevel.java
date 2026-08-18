package com.xiaotao.saltedfishcloud.service.permission;

import lombok.Getter;

/**
 * 用户对某 UID 资源的访问级别。
 * <p>
 * 级别由低到高：{@link #NONE} &lt; {@link #READ} &lt; {@link #WRITE} &lt; {@link #MANAGE}。
 * 该枚举是核心与插件（如团队空间）之间的权限契约：插件通过
 * {@link UidAccessProvider} 返回某用户对指定 UID 的访问级别，核心据此放行文件访问。
 */
@Getter
public enum UidAccessLevel {
    /**
     * 无任何访问权限
     */
    NONE(0),

    /**
     * 可读取资源（查看/下载/预览）
     */
    READ(1),

    /**
     * 可写资源（上传/新建/改名/移动/删除/覆盖）
     */
    WRITE(2),

    /**
     * 完全控制资源（含成员/权限管理等插件自定义语义）
     */
    MANAGE(3);

    /**
     * 级别数值，用于比较大小
     */
    private final int order;

    UidAccessLevel(int order) {
        this.order = order;
    }
}
