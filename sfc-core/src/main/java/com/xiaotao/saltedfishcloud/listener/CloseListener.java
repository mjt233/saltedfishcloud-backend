package com.xiaotao.saltedfishcloud.listener;

import com.xiaotao.saltedfishcloud.init.FileDBSynchronizer;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component

@RequiredArgsConstructor
public class CloseListener {
    private final FileDBSynchronizer synchronizer;
    @EventListener(ContextClosedEvent.class)
    public void onApplicationEvent(ContextClosedEvent event) {
        synchronizer.stop();
    }
}
