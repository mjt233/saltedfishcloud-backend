package com.sfc.ext.webdav.model.po;

import com.xiaotao.saltedfishcloud.model.template.AuditModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(indexes = {
        @Index(name = "idx_webdav_auth_uid", columnList = "uid", unique = true),
        @Index(name = "username_index", columnList = "username", unique = true)
})
@Setter
@Getter
public class WebDavAuth extends AuditModel {
    /**
     * 摘要值，用于计算WebDAV的digest认证
     * <p>A1 = username : realm : password</p>
     * <p>Response = MD5(MD5(A1) : nonce : MD5(A2))</p>
     */
    @Column(length = 32)
    private String a1Md5;

    private String username;
}
