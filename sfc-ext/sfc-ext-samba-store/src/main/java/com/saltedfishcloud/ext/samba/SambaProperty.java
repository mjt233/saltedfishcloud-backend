package com.saltedfishcloud.ext.samba;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Builder
@Data
@EqualsAndHashCode
public class SambaProperty {
    private String host;

    private String username;

    private String password;

    private Integer port;

    private String shareName;

    private String basePath;
}
