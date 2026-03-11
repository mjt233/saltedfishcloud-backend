package com.xiaotao.saltedfishcloud.model.po;

import com.xiaotao.saltedfishcloud.enums.OpenAuthorizationScope;
import com.xiaotao.saltedfishcloud.model.template.AuditModel;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统用户授权给第三方应用的记录
 */
@Entity
@Data
@EqualsAndHashCode(callSuper = true)
@Table(indexes = {
        @Index(name = "idx_app_id", columnList = "appId"),
        @Index(name = "idx_app_uid", columnList = "appId,uid"),
})
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

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appId", insertable = false, updatable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private ThirdPartyApp thirdPartyApp;
}
