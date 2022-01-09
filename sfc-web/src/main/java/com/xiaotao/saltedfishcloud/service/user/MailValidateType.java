package com.xiaotao.saltedfishcloud.service.user;

/**
 * 邮件验证类型
 */
public enum MailValidateType {
    VERIFY_MAIL,    // 验证邮箱
    BIND_MAIL,      // 绑定/更改邮箱
    RESET_PASSWORD  // 重置密码
}
