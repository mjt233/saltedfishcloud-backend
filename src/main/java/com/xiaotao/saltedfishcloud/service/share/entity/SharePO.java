package com.xiaotao.saltedfishcloud.service.share.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import javax.persistence.*;
import java.util.Date;

@EntityListeners(AuditingEntityListener.class)
@Entity
@Table(name = "share")
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SharePO {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private Integer uid;
    private String nid;
    private String verification;

    @Enumerated(EnumType.STRING)
    private ShareType type;
    private String extractCode;
    private String name;

    @CreatedDate
    private Date createdAt;
    private Date expiredAt;

    public SharePO(Integer uid, String nid, ShareType type, Date expiredAt) {
        this.uid = uid;
        this.nid = nid;
        this.type = type;
        this.expiredAt = expiredAt;
    }

    public static SharePO valueOf(ShareDTO shareDTO, ShareType type, String nid, int uid) {
        SharePO sharePO = new SharePO();
        sharePO.setExpiredAt(shareDTO.getExpiredAt());
        sharePO.setExtractCode(shareDTO.getExtractCode());
        sharePO.setNid(nid);
        sharePO.setUid(uid);
        sharePO.setType(type);
        sharePO.setVerification(SecureUtils.getUUID());
        sharePO.setName(shareDTO.getName());
        return sharePO;
    }

    public boolean validateExtractCode(String code) {
        return extractCode == null || extractCode.equals(code);
    }

    public boolean isExpired() {
        return expiredAt != null && expiredAt.getTime() < System.currentTimeMillis();
    }
}
