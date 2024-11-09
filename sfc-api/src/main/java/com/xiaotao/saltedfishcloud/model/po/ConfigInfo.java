package com.xiaotao.saltedfishcloud.model.po;

import com.xiaotao.saltedfishcloud.constant.SysConfigName;
import lombok.*;

import jakarta.persistence.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "config")
public class ConfigInfo {
    /**
     * @see SysConfigName
     */
    @Id
    @Column(name = "`key`", length = 64)
    private String key;

    @Column(length = 512)
    private String value;
}
