package com.xiaotao.saltedfishcloud.ext;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.PropertyResolver;

import java.util.ArrayList;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "plugin")
@Data
public class PluginProperty {

    /**
     * 额外的资源路径
     */
    private String[] extraResource = new String[0];

    public static PluginProperty loadFromPropertyResolver(PropertyResolver resolver) {
        PluginProperty property = new PluginProperty();
        List<String> resources = new ArrayList<>();
        int count = 0;
        String t;
        do {
             t = resolver.getProperty("plugin.extra-resource[" + count + "]");
             if (t != null) {
                 resources.add(t);
                 ++count;
             }
        } while (t != null);

        property.setExtraResource(resources.toArray(new String[0]));
        return property;
    }
}
