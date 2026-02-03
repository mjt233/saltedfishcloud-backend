package com.sfc.ext.webdav.model.resource;

import java.util.Date;

public interface IWebDavItem {
    String getName();

    void setName(String name);

    Date getCreateDate();

    void setCreateDate(Date date);
}
