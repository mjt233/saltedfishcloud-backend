package com.sfc.quickshare.model;

import com.xiaotao.saltedfishcloud.model.template.AuditModel;
import lombok.*;
import org.hibernate.validator.constraints.Length;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.util.Date;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(indexes = {
        @Index(name = "idx_uid", columnList = "uid"),
        @Index(name = "idx_expired", columnList = "expiredAt")
})
public class QuickShare extends AuditModel {

    /**
     * 文件名
     */
    @Length(max = 255)
    private String fileName;

    /**
     * 文件提取码
     */
    @Length(min = 1, max = 255)
    @NotNull
    private String code;

    /**
     * 文件大小
     */
    private Long size;

    /**
     * 过期日期
     */
    private Date expiredAt;

    /**
     * 留言
     */
    @Length(max = 255)
    private String message;
}
