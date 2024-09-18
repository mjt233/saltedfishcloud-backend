package com.sfc.webshell.model.po;

import com.xiaotao.saltedfishcloud.model.template.AuditModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.Length;
import org.springframework.context.annotation.Description;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Index;
import javax.persistence.Table;
import javax.validation.constraints.NotBlank;

/**
 * shell命令执行记录
 */
@Entity
@Getter
@Setter
@Table(indexes = {
        @Index(name = "idx_uid", columnList = "uid")
})
public class ShellExecuteRecord extends AuditModel {
    /**
     * 执行的命令
     */
    @Column(nullable = false, length = 800)
    @Length(max = 800)
    @NotBlank
    private String cmd;

    /**
     * 工作目录
     */
    private String workDir;

    /**
     * 执行主机
     */
    private String host;
}
