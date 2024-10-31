package com.sfc.onlyoffice.service;

import com.onlyoffice.manager.document.DocumentManager;
import com.onlyoffice.manager.security.JwtManager;
import com.onlyoffice.manager.settings.SettingsManager;
import com.onlyoffice.manager.url.UrlManager;
import com.onlyoffice.model.documenteditor.config.document.Permissions;
import com.onlyoffice.service.documenteditor.config.DefaultConfigService;
import org.springframework.stereotype.Service;

@Service
public class OnlyOfficeConfigServiceImpl extends DefaultConfigService {
    public OnlyOfficeConfigServiceImpl(final DocumentManager documentManager, final UrlManager urlManager,
                                       final JwtManager jwtManager, final SettingsManager settingsManager) {
        super(documentManager, urlManager, jwtManager, settingsManager);
    }

    @Override
    public Permissions getPermissions(final String fileId) {
        Permissions permissions = Permissions.builder()
                .edit(true)
                .chat(true)
                .build();
        return permissions;
    }

}