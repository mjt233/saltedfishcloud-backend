package com.sfc.onlyoffice.manager;

import com.onlyoffice.manager.settings.DefaultSettingsManager;
import com.onlyoffice.model.properties.DocsIntegrationSdkProperties;
import com.onlyoffice.model.properties.docsintegrationsdk.DocumentServerProperties;
import com.onlyoffice.model.properties.docsintegrationsdk.documentserver.SecurityProperties;
import com.onlyoffice.model.settings.SettingsConstants;
import com.sfc.onlyoffice.model.OfficeConfigProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Properties;

@Component
public class SettingManagerImpl extends DefaultSettingsManager {

    @Autowired
    private OfficeConfigProperty officeConfigProperty;

    private static Properties properties = new Properties();

    private final DocsIntegrationSdkProperties docsIntegrationSdkProperties = new DocsIntegrationSdkProperties();

    {
        docsIntegrationSdkProperties.setDocumentServer(new DocumentServerProperties());
        docsIntegrationSdkProperties.getDocumentServer().setSecurity(new SecurityProperties());
        docsIntegrationSdkProperties.getDocumentServer().getSecurity().setLeeway(10L);
    }

    @Override
    public String getSetting(final String name) {
        if (SettingsConstants.SECURITY_KEY.equals(name)) {
            return officeConfigProperty.getJwtSecret();
        }
        return properties.getProperty(name);
    }

    @Override
    public void setSetting(final String name, final String value) {
        properties.setProperty(name, value);
        if (SettingsConstants.SECURITY_KEY.equals(name)) {
            officeConfigProperty.setJwtSecret(value);
        }
    }

    @Override
    public Boolean isSecurityEnabled() {
        return officeConfigProperty.getEnableJwt();
    }

    @Override
    public String getSecurityKey() {
        return officeConfigProperty.getJwtSecret();
    }

    @Override
    public Boolean isIgnoreSSLCertificate() {
        return officeConfigProperty.getIsIgnoreSsl();
    }


    @Override
    public DocsIntegrationSdkProperties getDocsIntegrationSdkProperties() {
        return docsIntegrationSdkProperties;
    }
}
