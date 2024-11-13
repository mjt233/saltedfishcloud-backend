package com.xiaotao.saltedfishcloud.model.po;

import com.xiaotao.saltedfishcloud.model.template.AuditModel;
import com.xiaotao.saltedfishcloud.validator.annotations.UID;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.Hibernate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.validation.annotation.Validated;

import java.util.Objects;

@Getter
@Setter
@ToString
@Entity
@Table(
        name = "mount_point",
        indexes = {
                @Index(name = "idx_uid", columnList = "uid,nid")
        }
)
@EntityListeners(AuditingEntityListener.class)
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Validated
public class MountPoint extends AuditModel {

    @UID
    private Long uid;

    /**
     * 挂载点所在的节点id
     */
    @NotNull
    private String nid;

    /**
     * 协议
     */
    @NotNull
    private String protocol;

    /**
     * 文件系统参数
     */
    private String params;

    /**
     * 委托存储记录
     */
    private Boolean isProxyStoreRecord;

    /**
     * 挂载的目录名称
     */
    @NotNull
    private String name;

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

    /**
     * 创建节点时是否立即同步文件记录信息
     */
    @Transient
    private Boolean initRecord;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        MountPoint that = (MountPoint) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
