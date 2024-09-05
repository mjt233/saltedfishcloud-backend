package com.xiaotao.saltedfishcloud.model.po;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import org.hibernate.Hibernate;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.util.Objects;

@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Entity
@Table(name = "node_list")
@Builder
public class NodeInfo {

    @Id
    @GeneratedValue(generator="system_uuid")
    @GenericGenerator(name="system_uuid",strategy="uuid")
    private String id;

    private Long uid;

    private String name;

    private String parent;

    @Column(name = "mount_id")
    private Long mountId;

    /**
     * 是否为挂载点下的目录节点
     */
    @Column(name = "is_mount")
    private Boolean isMount;

    /**
     * 节点本身表示的路径
     */
    @Transient
    private String path;

    public boolean isRootNode() {
        return id.length() < 32;
    }

    public static NodeInfo getRootNode(long uid) {
        NodeInfo info = new NodeInfo();
        info.setName("");
        info.setId("" + uid);
        info.setUid(uid);
        return info;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        NodeInfo nodeInfo = (NodeInfo) o;
        return id != null && Objects.equals(id, nodeInfo.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
