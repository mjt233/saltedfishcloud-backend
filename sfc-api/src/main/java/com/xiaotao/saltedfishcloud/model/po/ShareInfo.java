package com.xiaotao.saltedfishcloud.model.po;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.xiaotao.saltedfishcloud.model.template.AuditModel;
import com.xiaotao.saltedfishcloud.service.share.entity.ShareDTO;
import com.xiaotao.saltedfishcloud.service.share.entity.ShareType;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.util.StringUtils;

import javax.persistence.*;
import java.util.Date;

@EntityListeners(AuditingEntityListener.class)
@Entity
@Table(name = "share", indexes = {
        @Index(name = "uid_index", columnList = "uid")
})
@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ShareInfo extends AuditModel {
    private Long uid;
    /**
     * 分享类型为目录时，分享的资源本身表示的节点id。若分享类型为文件则表示文件的md5。
     */
    private String nid;

    /**
     * 分享的资源所在的节点的上级节点id<br>
     */
    private String parentId;

    /**
     * 分享的校验标识
     */
    private String verification;

    /**
     * 分享的文件大小
     */
    private Long size;

    /**
     * 分享的资源类型（文件 或 目录）
     */
    @Enumerated(EnumType.STRING)
    private ShareType type;

    /**
     * 分享提取码
     */
    private String extractCode;

    /**
     * 分享的文件或目录名称
     */
    private String name;

    /**
     * 分享过期日期
     */
    private Date expiredAt;

    @Transient
    private String username;

    @Transient
    private boolean validateSuccess = false;

    /**
     * 是否隐藏敏感属性，通过getter方法获取时候获得null（如：提取码，校验码，存储节点id）
     */
    @Transient
    @JsonIgnore
    private boolean hide = false;

    public ShareInfo(Long uid, String nid, ShareType type, Date expiredAt) {
        this.uid = uid;
        this.nid = nid;
        this.type = type;
        this.expiredAt = expiredAt;
    }

    public static ShareInfo valueOf(ShareDTO shareDTO, ShareType type, String nid, long uid) {
        ShareInfo shareInfo = new ShareInfo();
        shareInfo.setExpiredAt(shareDTO.getExpiredAt());
        shareInfo.setExtractCode(shareDTO.getExtractCode());
        shareInfo.setNid(nid);
        shareInfo.setUid(uid);
        shareInfo.setType(type);
        shareInfo.setVerification(SecureUtils.getUUID());
        shareInfo.setName(shareDTO.getName());
        return shareInfo;
    }

    /**
     * 验证提取码是否有效
     * @param code  提取码
     * @return  有效true，否则false
     */
    public boolean validateExtractCode(String code) {
        return !StringUtils.hasText(extractCode) || extractCode.equalsIgnoreCase(code);
    }

    public boolean isExpired() {
        return expiredAt != null && expiredAt.getTime() < System.currentTimeMillis();
    }

    public ShareInfo hideKeyAttr() {
        hide = true;
        return this;
    }

    public String getNid() {
        if (hide && !validateSuccess) return null;
        return nid;
    }

    public String getParentId() {
        if (hide) return null;
        return parentId;
    }

    public String getVerification() {
        if (hide && !validateSuccess) return null;
        return verification;
    }

    public String getExtractCode() {
        if (hide && !validateSuccess) return null;
        return extractCode;
    }

    public boolean isNeedExtractCode() {
        return StringUtils.hasLength(extractCode);
    }
}
