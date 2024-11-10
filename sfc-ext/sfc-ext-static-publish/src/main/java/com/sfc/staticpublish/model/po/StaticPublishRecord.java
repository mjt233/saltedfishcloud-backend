package com.sfc.staticpublish.model.po;

import com.sfc.staticpublish.constants.AccessWay;
import com.xiaotao.saltedfishcloud.model.template.AuditModel;
import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.*;
import java.util.Objects;

/**
 * 静态站点目录发布记录
 */
@Entity
@Table(indexes = {
        @Index(name = "uid_idx", columnList = "uid"),
        @Index(name = "name_idx", columnList = "siteName"),
        @Index(name = "user_name_idx", columnList = "username,siteName")
})
@Getter
@Setter
public class StaticPublishRecord extends AuditModel {

    /**
     * 部署名（站点名）
     */
    private String siteName;

    /**
     * 发布者用户名
     */
    private String username;

    /**
     * 访问方式
     * @see AccessWay
     */
    private Integer accessWay;

    /**
     * 发布目录路径
     */
    private String path;

    /**
     * 是否开启index.html主页
     */
    private Boolean isEnableIndex;

    /**
     * 是否开启目录访问列出文件列表
     */
    private Boolean isEnableFileList;

    /**
     * 是否需要登录
     */
    private Boolean isNeedLogin;

    /**
     * 自定义登录用户名
     */
    @Column(length = 32)
    private String loginUsername;

    /**
     * 自定义登录密码
     */
    private String loginPassword;

    @Transient
    public boolean isByHost() {
        return Objects.equals(AccessWay.BY_HOST, accessWay);
    }

    @Transient
    public boolean isByPath() {
        return Objects.equals(AccessWay.BY_PATH, accessWay);
    }

    @Transient
    public boolean isByDirectRootPath() {
        return Objects.equals(AccessWay.BY_DIRECT_ROOT_PATH, accessWay);
    }
}
