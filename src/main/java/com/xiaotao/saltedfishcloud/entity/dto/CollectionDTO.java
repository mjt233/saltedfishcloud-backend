package com.xiaotao.saltedfishcloud.entity.dto;

import com.xiaotao.saltedfishcloud.entity.po.CollectionField;
import com.xiaotao.saltedfishcloud.utils.ByteSize;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.Date;
import java.util.List;

@Data
public class CollectionDTO {
    private String describe;
    @NotBlank
    private String title;
    private String nickname;
    private Long maxSize = ByteSize._1MiB * 128L;
    private Boolean allowAnonymous = true;
    private Integer allowMax = 100;
    private String pattern;
    private List<CollectionField> field;

    @NotNull
    private String saveNode;

    @NotNull
    private Date expiredAt;
}
