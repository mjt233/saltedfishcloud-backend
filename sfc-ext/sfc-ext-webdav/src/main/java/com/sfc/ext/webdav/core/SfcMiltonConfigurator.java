package com.sfc.ext.webdav.core;

import io.milton.servlet.DefaultMiltonConfigurator;

public class SfcMiltonConfigurator extends DefaultMiltonConfigurator {
    @Override
    protected void build() {
        builder.setSecurityManager(new SfcSecurityManager());
        super.build();
    }
}
