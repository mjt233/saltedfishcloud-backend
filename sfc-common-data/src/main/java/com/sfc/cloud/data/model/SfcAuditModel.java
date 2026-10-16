package com.sfc.cloud.data.model;


import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.Date;

/**
 * 带审计字段与数据持有人字段的实体类模板。
 *
 * <p>在 {@link SfcBaseModel} 的基础上追加：</p>
 * <ul>
 *   <li>{@code uid} —— 数据所属人；</li>
 *   <li>{@code createAt} —— 创建时间，由 Spring Data JPA 审计自动填充；</li>
 *   <li>{@code updateAt} —— 更新时间，由 Spring Data JPA 审计自动填充。</li>
 * </ul>
 *
 * <p><b>列名兼容性</b>：三个字段的列名（{@code uid} / {@code create_at} / {@code update_at}）
 * 与宿主 {@code AuditModel} 逐字一致，不得改动，否则会与既有数据库结构及数据不兼容。</p>
 *
 * <p>使用时需在应用侧启用 JPA 审计
 * （{@code @EnableJpaAuditing} 或等价的配置），{@link AuditingEntityListener} 才会生效。</p>
 */
@EntityListeners(AuditingEntityListener.class)
@MappedSuperclass
@Getter
@Setter
public class SfcAuditModel extends SfcBaseModel {
    /**
     * 数据所属人
     */
    @Column(name = "uid")
    private Long uid;

    /**
     * 数据创建日期
     */
    @CreatedDate
    @Column(name = "create_at")
    private Date createAt;

    /**
     * 数据更新日期
     */
    @LastModifiedDate
    @Column(name = "update_at")
    private Date updateAt;
}
