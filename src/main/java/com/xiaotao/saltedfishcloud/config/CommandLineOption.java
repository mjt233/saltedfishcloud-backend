package com.xiaotao.saltedfishcloud.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CommandLineOption {
    private static ApplicationArguments ARGS;
    public static final String SWITCH = "switch";

    CommandLineOption(ApplicationArguments applicationArguments) {
        ARGS = applicationArguments;
    }

    public static String getValue(String key) {
        return getValue(key, null);
    }

    public static String getValue(String key, String defaultValue) {
        List<String> val = ARGS.getOptionValues(key);
        if (val == null || val.isEmpty()) {
            return defaultValue;
        } else {
            return val.get(0);
        }
    }

}
