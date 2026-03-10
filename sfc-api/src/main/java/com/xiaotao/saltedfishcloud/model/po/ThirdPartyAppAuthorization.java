package com.xiaotao.saltedfishcloud.model.po;

import com.xiaotao.saltedfishcloud.enums.OpenAuthorizationScope;
import com.xiaotao.saltedfishcloud.model.template.AuditModel;
import jakarta.persistence.Entity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统用户授权给第三方应用的记录
 */
@Entity
@Data
@EqualsAndHashCode(callSuper = true)
public class ThirdPartyAppAuthorization extends AuditModel {

    /**
     * 第三方应用id
     */
    private Long appId;

    /**
     * 授权范围，全小写，多个权限使用空格分割。
     * @see OpenAuthorizationScope
     */
    private String scope;
}
