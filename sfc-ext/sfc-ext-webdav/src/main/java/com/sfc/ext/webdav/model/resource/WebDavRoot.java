package com.sfc.ext.webdav.model.resource;

import lombok.Data;

@Data
public class WebDavRoot {
    private Long uid;
    private String name = "ROOT";

    public WebDavRoot(Long uid) {
        this.uid = uid;
    }

    public WebDavRoot() {
    }
}
