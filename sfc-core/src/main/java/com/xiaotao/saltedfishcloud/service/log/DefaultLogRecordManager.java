package com.xiaotao.saltedfishcloud.service.log;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.AsyncAppenderBase;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import com.xiaotao.saltedfishcloud.constant.SysConfigName;
import com.xiaotao.saltedfishcloud.model.ClusterNodeInfo;
import com.xiaotao.saltedfishcloud.model.po.LogRecord;
import com.xiaotao.saltedfishcloud.service.ClusterService;
import com.xiaotao.saltedfishcloud.service.config.ConfigService;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.boot.logging.LogLevel;
import org.springframework.context.event.EventListener;
import org.springframework.data.util.Lazy;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultLogRecordManager implements LogRecordManager {
    private boolean isEnable;
    private boolean isEnableAutoLog;
    private Level recordLevel;

    private final ConfigService configService;
    private final ClusterService clusterService;
    private final LogRecordService logRecordService;

    private Lazy<ClusterNodeInfo> clusterNode;

    @Override
    public void saveRecord(LogRecord logRecord) {
        if (!isEnable) {
            return;
        }
        if (logRecord.getProducerPid() == null || logRecord.getProducerHost() == null) {
            logRecord.setProducerPid(clusterNode.get().getPid());
            logRecord.setProducerHost(clusterNode.get().getHost());
        }
        if (logRecord.getUid() == null) {
            logRecord.setUid(SecureUtils.getCurrentUid());
        }

        // todo 通过配置定义具体的日志存储记录方式，如使用第三方云服务提供的日志服务、Hadoop等服务
        logRecordService.saveRecord(logRecord);
    }

    public class SfcCustomAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

        @Override
        protected void append(ILoggingEvent event) {
            if (!isEnableAutoLog || !isEnable) {
                return;
            }
            if (recordLevel == null || event.getLevel().levelInt < recordLevel.levelInt) {
                return;
            }
            StringBuilder msgAbstract = new StringBuilder(event.getFormattedMessage());

            StringBuilder detail =  new StringBuilder(event.getFormattedMessage());
            if (event.getThrowableProxy() != null) {
                IThrowableProxy throwableProxy = event.getThrowableProxy();
                msgAbstract.append(" ").append(throwableProxy.getClassName()).append(": ").append(throwableProxy.getMessage());
                detail.append("\n")
                        .append(throwableProxy.getClassName()).append(": ").append(throwableProxy.getMessage())
                        .append("\n");
                for (StackTraceElementProxy traceElementProxy : throwableProxy.getStackTraceElementProxyArray()) {
                    detail.append("\t").append(traceElementProxy.toString()).append("\n");
                }
            }
            saveRecord(LogRecord.builder()
                    .level(LogLevel.valueOf(event.getLevel().toString()))
                    .msgAbstract(msgAbstract.toString())
                    .msgDetail(detail.toString())
                    .type("logger")
                    .producerThread(event.getThreadName())
                    .build());
        }
    }

    /**
     * 初始化日志输出配置
     */
    public void init() {
        clusterNode = Lazy.of(clusterService::getSelf);

        // 给根logger添加通用的日志appender接收所有的日志进行二次处理以便采集日志
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        ch.qos.logback.classic.Logger logger = context.getLogger(Logger.ROOT_LOGGER_NAME);
        Appender<ILoggingEvent> newAppender = new SfcCustomAppender();
        newAppender.setContext(logger.getLoggerContext());
        newAppender.setName("SFC_CUSTOM");
        newAppender.start();
        logger.addAppender(newAppender);

        // 绑定日志的配置信息到变量
        this.isEnable = "true".equals(configService.getConfig(SysConfigName.Log.ENABLE_LOG));
        this.isEnableAutoLog = "true".equals(configService.getConfig(SysConfigName.Log.ENABLE_AUTO_LOG));
        this.recordLevel = Level.valueOf(configService.getConfig(SysConfigName.Log.AUTO_LOG_LEVEL, Level.WARN.levelStr));

        configService.addAfterSetListener(SysConfigName.Log.ENABLE_LOG, val -> this.isEnable = "true".equals(val));
        configService.addAfterSetListener(SysConfigName.Log.ENABLE_AUTO_LOG, val -> this.isEnableAutoLog = "true".equals(val));
        configService.addAfterSetListener(SysConfigName.Log.AUTO_LOG_LEVEL, val -> this.recordLevel = Level.toLevel(configService.getConfig(SysConfigName.Log.AUTO_LOG_LEVEL, Level.WARN.levelStr)));
        log.info("日志记录服务初始化");
    }

    @EventListener(ApplicationStartedEvent.class)
    public void registerLogbackAppender() {
        init();
    }
}
