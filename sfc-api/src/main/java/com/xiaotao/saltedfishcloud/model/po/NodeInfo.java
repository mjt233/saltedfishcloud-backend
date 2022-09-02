package com.xiaotao.saltedfishcloud.model.po;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class NodeInfo {
    private String name;
    private Integer uid;
    private String id;
    private String parent;

    public boolean isRootNode() {
        return id.length() < 32;
    }

    public static NodeInfo getRootNode(int uid) {
        NodeInfo info = new NodeInfo();
        info.setName("");
        info.setId("" + uid);
        info.setUid(uid);
        return info;
    }
}
