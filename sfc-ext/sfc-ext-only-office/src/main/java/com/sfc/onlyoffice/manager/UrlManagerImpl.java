package com.sfc.onlyoffice.manager;

import com.onlyoffice.manager.settings.SettingsManager;
import com.onlyoffice.manager.url.DefaultUrlManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class UrlManagerImpl extends DefaultUrlManager {
    @Autowired
    public UrlManagerImpl(SettingsManager settingsManager) {
        super(settingsManager);
    }
}
