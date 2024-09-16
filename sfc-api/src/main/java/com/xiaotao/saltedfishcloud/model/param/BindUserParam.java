package com.xiaotao.saltedfishcloud.model.param;

import lombok.Data;

@Data
public class BindUserParam {
    /**
     * 第三方登录操作id
     */
    private String actionId;

    /**
     * 自动绑定（根据第三方登录时自动匹配到的用户绑定，邮箱相同 或 已登录状态下进行第三方账号绑定时传入true）
     */
    private Boolean autoBind;

    /**
     * 待绑定的用户名/邮箱
     */
    private String account;

    /**
     * 待绑定的用户密码
     */
    private String password;
}
