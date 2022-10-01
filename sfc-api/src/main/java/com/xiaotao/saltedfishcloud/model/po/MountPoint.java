package com.xiaotao.saltedfishcloud.model.po;

import com.xiaotao.saltedfishcloud.validator.annotations.UID;
import lombok.*;
import org.hibernate.Hibernate;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Proxy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.validation.annotation.Validated;

import javax.persistence.*;
import java.util.Date;
import java.util.Objects;

@Getter
@Setter
@ToString
@Entity
@Proxy(lazy = false)
@Table(name = "mount_point")
@EntityListeners(AuditingEntityListener.class)
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Validated
public class MountPoint {

    @Id
    @GeneratedValue(generator = "snowflake")
    @GenericGenerator(name = "snowflake", strategy = "com.xiaotao.saltedfishcloud.utils.identifier.SnowFlakeIdGenerator")
    private Long id;

    @UID
    private Long uid;

    /**
     * 挂载点所在的节点id
     */
    private String nid;

    /**
     * 协议
     */
    private String protocol;

    /**
     * 文件系统参数
     */
    private String params;

    /**
     * 挂载的目录名称
     */
    private String name;

    @Column(name = "create_at")
    @CreatedDate
    private Date createAt;

    /**
     * 该挂载点的完整路径
     */
    @Transient
    private String path;

    /**
     * 挂载的节点所处节点的路径(nid对应的路径)
     */
    @Transient
    private String parentPath;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        MountPoint that = (MountPoint) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
