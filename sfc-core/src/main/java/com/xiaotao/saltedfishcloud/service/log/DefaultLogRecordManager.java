package com.xiaotao.saltedfishcloud.service.log;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import com.xiaotao.saltedfishcloud.ext.PluginManager;
import com.xiaotao.saltedfishcloud.model.ClusterNodeInfo;
import com.xiaotao.saltedfishcloud.model.ConfigNode;
import com.xiaotao.saltedfishcloud.model.PluginInfo;
import com.xiaotao.saltedfishcloud.model.config.SysLogConfig;
import com.xiaotao.saltedfishcloud.model.po.LogRecord;
import com.xiaotao.saltedfishcloud.service.ClusterService;
import com.xiaotao.saltedfishcloud.service.config.ConfigService;
import com.xiaotao.saltedfishcloud.utils.ExtUtils;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.xiaotao.saltedfishcloud.utils.TypeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.util.Lazy;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultLogRecordManager implements LogRecordManager {

    private final ConfigService configService;
    private final ClusterService clusterService;
    private final LogRecordService logRecordService;
    private final PluginManager pluginManager;
    private final SysLogConfig config;

    private Lazy<ClusterNodeInfo> clusterNode;

    private Appender<ILoggingEvent> consoleAppender;

    @Override
    public void saveRecord(LogRecord logRecord) {
        if (!Objects.equals(true, config.getEnableLog())) {
            return;
        }
        if (logRecord.getProducerPid() == null || logRecord.getProducerHost() == null) {
            logRecord.setProducerPid(clusterNode.get().getPid());
            logRecord.setProducerHost(clusterNode.get().getHost());
        }
        if (logRecord.getUid() == null) {
            logRecord.setUid(SecureUtils.getCurrentUid());
        }
        if (logRecord.getProducerThread() == null) {
            logRecord.setProducerThread(Thread.currentThread().getName());
        }

        // todo 通过配置定义具体的日志存储记录方式，如使用第三方云服务提供的日志服务、Hadoop等服务
        logRecordService.saveRecord(logRecord);
    }

    public class SfcCustomAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

        @Override
        protected void append(ILoggingEvent event) {
            if (!Objects.equals(true, config.getEnableLog()) || !Objects.equals(true, config.getEnableAutoLog())) {
                return;
            }
            Level recordLevel = Optional.ofNullable(config.getAutoLogLevel()).map(LogLevel::getLevel).orElse(null);
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
                    .level(com.xiaotao.saltedfishcloud.service.log.LogLevel.valueOf(event.getLevel().toString()))
                    .msgAbstract(msgAbstract.toString())
                    .msgDetail(detail.toString())
                    .type("logger")
                    .producerThread(event.getThreadName())
                    .build());
        }
    }

    /**
     * 注册为一个内置插件，以便可以有一个管理端单独的菜单
     */
    private void registerAsPlugin() throws IOException {
        String pluginPath = "build-in-plugin/sys-log";
        ClassLoader classLoader = this.getClass().getClassLoader();
        PluginInfo pluginInfo = ExtUtils.getPluginInfo(classLoader, pluginPath);
        List<ConfigNode> nodeList = ExtUtils.getPluginConfigNodeFromLoader(classLoader, pluginPath);
        pluginManager.registerPluginResource("sysLog", pluginInfo, nodeList, pluginPath, classLoader);
    }

    /**
     * 初始化日志输出配置
     */
    public void init() throws IOException {
        clusterNode = Lazy.of(clusterService::getSelf);

        this.registerAsPlugin();

        // 给根logger添加通用的日志appender接收所有的日志进行二次处理以便采集日志
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        ch.qos.logback.classic.Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
        Appender<ILoggingEvent> newAppender = new SfcCustomAppender();
        newAppender.setContext(rootLogger.getLoggerContext());
        newAppender.setName("SFC_CUSTOM");
        newAppender.start();
        rootLogger.addAppender(newAppender);

        this.consoleAppender = rootLogger.getAppender("CONSOLE");
        if (Objects.equals(true, config.getDisableConsoleOutput())) {
            rootLogger.detachAppender(this.consoleAppender);
        }
        configService.addAfterSetListener("sys.log.disable_console_output", val -> {
            if(TypeUtils.toBoolean(val)) {
                rootLogger.detachAppender(this.consoleAppender);
            } else {
                rootLogger.addAppender(this.consoleAppender);
            }
        });

        log.info("日志记录服务初始化");
    }

    @EventListener(ApplicationStartedEvent.class)
    public void registerLogbackAppender() throws IOException {
        init();
    }
}
