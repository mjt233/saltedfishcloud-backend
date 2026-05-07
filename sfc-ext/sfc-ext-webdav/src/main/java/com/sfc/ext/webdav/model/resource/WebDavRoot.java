package com.sfc.ext.webdav.model.resource;

import lombok.Data;

import java.util.Date;

@Data
public class WebDavRoot {
    private Long uid;
    private String name = "ROOT";

    private Date createDate;

    private Date modifiedDate;

    public WebDavRoot(Long uid) {
        this.uid = uid;
        Date now = new Date();
        this.createDate = now;
        this.modifiedDate = now;
    }

    public WebDavRoot() {
        this(null);
    }
}
