package com.sfc.ext.webdav.enums;

import lombok.Getter;

@Getter
public enum ResourceArea {
    PRIVATE("private"),
    PUBLIC("public");

    private final String name;
    ResourceArea(String name) {
        this.name = name;
    }

}
