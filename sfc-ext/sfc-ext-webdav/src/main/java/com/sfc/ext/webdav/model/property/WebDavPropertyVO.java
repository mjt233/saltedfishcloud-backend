package com.sfc.ext.webdav.model.property;

import com.sfc.common.utils.SfcStringUtils;

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
        return Optional.ofNullable(delegate.getDisplayUrl()).filter(SfcStringUtils::hasText).orElseGet(() -> "{protocol}//{hostname}:" + delegate.getListenPort());
    }
}
