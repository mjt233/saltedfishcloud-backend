package com.xiaotao.saltedfishcloud.model.dto;

import com.xiaotao.saltedfishcloud.model.po.CollectionField;
import com.sfc.constant.ByteSize;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
public class CollectionDTO {
    private String describe;
    @NotBlank
    private String title;
    @NotBlank
    private String nickname;
    private Long maxSize = ByteSize._1MiB * 128L;
    private Boolean allowAnonymous = true;
    private Integer allowMax = 100;
    private String pattern;
    private List<CollectionField> field;
    private String extPattern;

    public CollectionDTO(@NotBlank String title, @NotNull String saveNode, @NotNull Date expiredAt, String nickname) {
        this.title = title;
        this.saveNode = saveNode;
        this.expiredAt = expiredAt;
        this.nickname = nickname;
    }

    @NotNull
    private String saveNode;

    @NotNull
    private Date expiredAt;
}
