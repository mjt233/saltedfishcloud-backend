package com.sfc.onlyoffice.manager;

import com.onlyoffice.manager.document.DefaultDocumentManager;
import com.onlyoffice.manager.settings.SettingsManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DocumentManagerImpl extends DefaultDocumentManager {

    @Autowired
    public DocumentManagerImpl(SettingsManager settingsManager) {
        super(settingsManager);
    }

    @Override
    public String getDocumentKey(final String fileId, final boolean embedded) {
        return String.valueOf(fileId.hashCode());
    }

    @Override
    public String getDocumentName(final String fileId) {
        return "sample.docx";
    }
}
