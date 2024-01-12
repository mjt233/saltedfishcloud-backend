package com.xiaotao.saltedfishcloud.model.po;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
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
@Table(name = "share")
@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ShareInfo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long uid;
    private String nid;
    private String parentId;
    private String verification;
    private Long size;

    @Enumerated(EnumType.STRING)
    private ShareType type;
    private String extractCode;
    private String name;

    @CreatedDate
    private Date createdAt;
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
