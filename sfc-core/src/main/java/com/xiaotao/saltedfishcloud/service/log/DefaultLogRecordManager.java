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
import com.xiaotao.saltedfishcloud.model.CommonPageInfo;
import com.xiaotao.saltedfishcloud.model.ConfigNode;
import com.xiaotao.saltedfishcloud.model.PluginInfo;
import com.xiaotao.saltedfishcloud.model.config.SysLogConfig;
import com.xiaotao.saltedfishcloud.model.param.LogRecordQueryParam;
import com.xiaotao.saltedfishcloud.model.po.LogRecord;
import com.xiaotao.saltedfishcloud.service.ClusterService;
import com.xiaotao.saltedfishcloud.service.config.ConfigService;
import com.xiaotao.saltedfishcloud.utils.ExtUtils;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.util.Lazy;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultLogRecordManager implements LogRecordManager, InitializingBean {
    private final static String LOG_PREFIX = "[日志管理]";
    private final static String FULL_MSG = "日志存储线程池满，无法写入日志，跳过";

    private final Map<String, LogRecordStorage> storageMap = new ConcurrentHashMap<>();
    private List<LogRecordStorage> storageList = new CopyOnWriteArrayList<>();

    // 依赖bean
    private final ConfigService configService;
    private final ClusterService clusterService;
    private final PluginManager pluginManager;
    private final SysLogConfig config;

    /**
     * 当前节点信息
     */
    private Lazy<ClusterNodeInfo> selfClusterNode;

    /**
     * 原始控制台输出appender
     */
    private Appender<ILoggingEvent> consoleAppender;

    /**
     * 启用的主存储器
     */
    private String mainStorageName = "Database";

    /**
     * 日志存储执行线程池
     */
    private final Executor executor = new ThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors(),
            Runtime.getRuntime().availableProcessors() * 32,
            10,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(1),
            r -> new Thread(r, "log-async-storage"),
            (r, executor1) -> log.error(FULL_MSG)
    );

    /**
     * 设置主日志存储器
     * @param storageName  存储器名称
     */
    public void setMainStorageName(String storageName) {
        this.mainStorageName = storageName;
        switchMainStorageActive(storageName);
    }

    @Override
    public void saveRecord(LogRecord logRecord) {
        saveRecordAsync(logRecord).join();
    }


    @Override
    @SuppressWarnings("unchecked")
    public CompletableFuture<Void> saveRecordAsync(LogRecord logRecord) {
        if (!Objects.equals(true, config.getEnableLog())) {
            return CompletableFuture.completedFuture(null);
        }
        if (logRecord.getProducerPid() == null || logRecord.getProducerHost() == null) {
            logRecord.setProducerPid(selfClusterNode.get().getPid());
            logRecord.setProducerHost(selfClusterNode.get().getHost());
        }
        if (logRecord.getUid() == null) {
            logRecord.setUid(SecureUtils.getCurrentUid());
        }
        if (logRecord.getProducerThread() == null) {
            logRecord.setProducerThread(Thread.currentThread().getName());
        }

        CompletableFuture<Void>[] futures = (CompletableFuture<Void>[])storageList.stream()
                .filter(LogRecordStorage::isActive)
                .map(e -> CompletableFuture.runAsync(() -> e.saveRecord(logRecord), executor))
                .toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(futures);
    }

    public class SfcCustomAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {
        /**
         * 调用次数记录。通过记录调用次数抑以制循环调用<br>
         *
         */
        private final ThreadLocal<Boolean> inCallLocal = new ThreadLocal<>();

        @Override
        protected void append(ILoggingEvent event) {
            if (inCallLocal.get() != null) {
                return;
            }
            inCallLocal.set(true);
            try {
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
            } finally {
                inCallLocal.remove();
            }
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
        selfClusterNode = Lazy.of(clusterService::getSelf);

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

        // 监听参数变化，动态更新控制台的appender
        configService.addAfterSetListener(SysLogConfig::getDisableConsoleOutput, disableConsoleOutput -> {
            if(disableConsoleOutput) {
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

    @Override
    public CommonPageInfo<LogRecord> queryLog(LogRecordQueryParam param) {
        return Optional.ofNullable(getMainStorageName())
                .map(e -> e.query(param))
                .orElse(null);
    }

    @Override
    public void registerStorage(LogRecordStorage logRecordStorage) {
        storageMap.put(logRecordStorage.getName(), logRecordStorage);
        log.info("{}注册新的日志存储器{}", LOG_PREFIX, logRecordStorage.getName());
        this.refreshStorageList();
        this.switchMainStorageActive(mainStorageName);
    }

    @Override
    public boolean removeStorage(String storageName) {
        if(storageMap.remove(storageName) != null) {
            this.refreshStorageList();
            return true;
        } else {
            return false;
        }
    }

    private void refreshStorageList() {
        this.storageList = getAllStorage();
    }

    public LogRecordStorage getMainStorageName() {
        if (mainStorageName == null) {
            return null;
        }
        return storageMap.get(mainStorageName);
    }

    @Override
    public List<LogRecordStorage> getAllStorage() {
        return this.storageMap.values().stream().toList();
    }


    @Override
    public void afterPropertiesSet() throws Exception {
        String mainStorage = configService.getConfig(SysLogConfig::getMainLogRecordStorage, "Database");
        this.setMainStorageName(mainStorage);
        configService.addAfterSetListener(SysLogConfig::getMainLogRecordStorage, this::setMainStorageName);
    }

    protected void switchMainStorageActive(String storageName) {
        log.info("{}日志主存储器切换到{}", LOG_PREFIX, storageName);
        for (LogRecordStorage storage : this.storageList) {
            if (storage.getName().equals(storageName)) {
                log.info("{}[√]{}", LOG_PREFIX, storage.getClass().getSimpleName());
                storage.active();
            } else {
                log.info("{}[ ]{}", LOG_PREFIX, storage.getClass().getSimpleName());
                storage.stop();
            }
        }
    }
}
