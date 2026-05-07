package com.sfc.ext.webdav.model.resource;

import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 用于标记资源在被请求时，该资源在会话中需要登录才能访问
 */
@SuperBuilder
@NoArgsConstructor
public class UnAuthoriseWebDavItem extends WebDavItem implements UnAuthoriseResource {
    public static UnAuthoriseWebDavItem get(WebDavItem parent, String name) {
        return UnAuthoriseWebDavItem.builder()
                .name(PathUtils.getLastNode(name))
                .resourceArea(parent.getResourceArea())
                .path(StringUtils.appendPath(parent.getPath(), parent.getName()))
                .build();
    }

    public static UnAuthoriseWebDavItem get(WebDavItem parent) {
        return UnAuthoriseWebDavItem.builder()
                .name(parent.getName())
                .resourceArea(parent.getResourceArea())
                .path(parent.getPath())
                .build();
    }
}
