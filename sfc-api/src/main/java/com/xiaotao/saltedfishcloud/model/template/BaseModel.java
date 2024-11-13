package com.xiaotao.saltedfishcloud.model.template;

import com.xiaotao.saltedfishcloud.annotations.SnowFlakeId;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@MappedSuperclass
public class BaseModel implements Serializable {

    @Id
    @SnowFlakeId
    @Column(name = "id", columnDefinition = "BIGINT UNSIGNED COMMENT '主键'")
    private Long id;
}
