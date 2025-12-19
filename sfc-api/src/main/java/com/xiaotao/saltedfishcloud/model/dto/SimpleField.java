package com.xiaotao.saltedfishcloud.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SimpleField {
    @NotBlank
    private String name;
    @NotBlank
    private String value;
}
