package com.xiaotao.saltedfishcloud.po;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.var;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NodeInfo {
    private String name;
    private Integer uid;
    private String id;
    private String parent;

    public boolean isRootNode() {
        return parent.equals("" + id);
    }

    public static NodeInfo getRootNode(int uid) {
        var info = new NodeInfo();
        info.setName("");
        info.setId("" + uid);
        info.setUid(uid);
        return info;
    }
}
