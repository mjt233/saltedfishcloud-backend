package com.sfc.ext.webdav.model.property;

import com.xiaotao.saltedfishcloud.utils.StringUtils;

import java.util.Optional;

public class WebDavPropertyVO {
    private final WebDavProperty delegate;

    public WebDavPropertyVO(WebDavProperty delegate) {
        this.delegate = delegate;
    }

    public Boolean getIsEnable() {
        return delegate.getIsEnable();
    }

    public String getDisplayUrl() {
        return Optional.ofNullable(delegate.getDisplayUrl()).filter(StringUtils::hasText).orElseGet(() -> "{protocol}//{hostname}:" + delegate.getListenPort());
    }

    public Boolean getIsAllowAnonymous() {
        return delegate.getIsAllowAnonymous();
    }

}
