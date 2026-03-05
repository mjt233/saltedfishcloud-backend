package com.xiaotao.saltedfishcloud.event.oauth;

import com.xiaotao.saltedfishcloud.model.po.ThirdPartyApp;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.List;

public class ThirdPartyAppDeleteEvent extends ApplicationEvent {
    @Getter
    private final List<ThirdPartyApp> apps;
    public ThirdPartyAppDeleteEvent(Object source, List<ThirdPartyApp> apps) {
        super(source);
        this.apps = apps;
    }
}
