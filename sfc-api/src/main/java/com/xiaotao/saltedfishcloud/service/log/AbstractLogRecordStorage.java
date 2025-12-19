package com.xiaotao.saltedfishcloud.service.log;

import com.xiaotao.saltedfishcloud.model.po.LogRecord;
import com.xiaotao.saltedfishcloud.service.config.ConfigService;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeansException;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.event.EventListener;

import java.io.IOException;

@Slf4j
public abstract class AbstractLogRecordStorage implements LogRecordStorage, ApplicationContextAware {
    private boolean isActive;
    private ApplicationContext applicationContext;

    @Override
    public boolean isActive() {
        return isActive;
    }

    @Override
    public void active() {
        isActive = true;
    }

    @Override
    public void stop() {
        isActive = false;
    }

    @Override
    public void saveRecord(LogRecord logRecord) {
        if (!isActive()) {
            return;
        }
        doSaveRecord(logRecord);
    }

    protected abstract void doSaveRecord(LogRecord logRecord);

    @Override
    public void setApplicationContext(@NotNull ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @EventListener(ApplicationStartedEvent.class)
    public void registerSelf() throws IOException {
        applicationContext.getBean(LogRecordManager.class)
                .registerStorage(applicationContext.getBean(this.getClass()));
    }
}
