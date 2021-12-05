package com.xiaotao.saltedfishcloud.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SimpleField {
    @NotBlank
    private String name;
    @NotBlank
    private String value;
}
