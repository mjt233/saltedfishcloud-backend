package com.sfc.ext.webdav.core;

import com.sfc.ext.webdav.core.propsource.Win32DatePropertySource;
import com.sfc.ext.webdav.core.propsource.QuotaPropertySource;
import io.milton.property.PropertySource;
import io.milton.servlet.DefaultMiltonConfigurator;

import java.util.ArrayList;
import java.util.List;

public class SfcMiltonConfigurator extends DefaultMiltonConfigurator {
    @Override
    protected void build() {
        builder.setSecurityManager(new SfcSecurityManager());
        if(builder.getPropertySources() == null) {
            builder.setPropertySources(new ArrayList<>());
        }
        List<PropertySource> propertySources = builder.getPropertySources();
        propertySources.add(new QuotaPropertySource());
        propertySources.add(new Win32DatePropertySource());
        super.build();
    }
}
