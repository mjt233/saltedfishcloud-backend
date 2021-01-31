package com.xiaotao.saltedfishcloud.po;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NodeInfo {
    private String name;
    private String id;
    private String parent;
}
