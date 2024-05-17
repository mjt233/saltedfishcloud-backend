package com.sfc.staticpublish.service.impl;

import com.sfc.rpc.annotation.RPCAction;
import com.sfc.rpc.annotation.RPCService;
import com.sfc.rpc.enums.RPCResponseStrategy;
import com.sfc.staticpublish.model.ServiceStatus;
import com.sfc.staticpublish.model.property.StaticPublishProperty;
import com.sfc.staticpublish.service.StaticPublishService;
import com.sfc.staticpublish.servlet.DispatchServlet;
import com.xiaotao.saltedfishcloud.service.ClusterService;
import com.xiaotao.saltedfishcloud.service.MQService;
import com.xiaotao.saltedfishcloud.service.config.ConfigService;
import com.xiaotao.saltedfishcloud.utils.PoolUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.webresources.StandardRoot;
import org.apache.tomcat.JarScanFilter;
import org.apache.tomcat.JarScanType;
import org.apache.tomcat.JarScanner;
import org.apache.tomcat.JarScannerCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import javax.servlet.ServletContext;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

class NoJarScanner implements JarScanner {
    @Override
    public void scan(JarScanType scanType, ServletContext context, JarScannerCallback callback) {
    }

    @Override
    public JarScanFilter getJarScanFilter() {
        return null;
    }

    @Override
    public void setJarScanFilter(JarScanFilter jarScanFilter) {

    }
}

@Service
@Slf4j
@RPCService(namespace = "static_publish")
public class StaticPublishServiceImpl implements StaticPublishService, ApplicationRunner {
    private final static String LOG_PREFIX = "[静态部署服务]";

    private final static String MQ_TOPIC_PROPERTY_CHANGE = "static_publish_property_change";

    private Tomcat tomcatInst;
    private boolean isRunning;
    private String lastError;

    @Autowired
    private DispatchServlet servlet;

    @Autowired
    private StaticPublishProperty property;

    @Autowired
    private ConfigService configService;

    @Autowired
    private MQService mqService;

    @Autowired
    private ClusterService clusterService;

    @Override
    public void start() throws LifecycleException, IOException {
        if (tomcatInst != null) {
            throw new IllegalArgumentException("服务已启动，无需重复启动");
        }
        log.info("{}启动内嵌Tomcat服务器中...", LOG_PREFIX);

        Tomcat tomcat = new Tomcat();
        tomcat.setAddDefaultWebXmlToWebapp(false);
        tomcat.setPort(Optional.ofNullable(property.getServerPort()).orElse(9999));

        Path tomcatTmpPath = Path.of(".").toAbsolutePath();
        Context ctx = tomcat.addWebapp("", tomcatTmpPath.toAbsolutePath().toString());
        ctx.setReloadable(false);
        StandardRoot root = new StandardRoot();

        ctx.setJarScanner(new NoJarScanner());
        ctx.setResources(root);
        ctx.setParentClassLoader(this.getClass().getClassLoader());
        ctx.addLifecycleListener(l -> {
            switch (l.getLifecycle().getState()) {
                case FAILED: lastError = Optional.ofNullable(l.getData()).map(Object::toString).orElse("");
                case STOPPED: isRunning = false;
            }
        });

        tomcat.addServlet("", "static-dispatcher", servlet);
        ctx.addServletMappingDecoded("/*", "static-dispatcher");
        tomcat.getConnector();
        tomcat.start();
        this.isRunning = true;
        this.tomcatInst = tomcat;

        log.info("{}内嵌Tomcat服务器启动成功", LOG_PREFIX);
        lastError = null;
    }

    @Override
    @EventListener(ContextClosedEvent.class)
    public void stop() throws LifecycleException {
        if (tomcatInst != null) {
            tomcatInst.stop();
            tomcatInst.destroy();
            this.isRunning = false;
            this.tomcatInst = null;
            log.info("{}内嵌Tomcat服务关闭成功", LOG_PREFIX);
        } else {
            log.info("{}内嵌Tomcat服务未开启，无需关闭", LOG_PREFIX);
        }
    }

    @Override
    public boolean isRunning() {
        return isRunning;
    }

    @Override
    @RPCAction(strategy = RPCResponseStrategy.SUMMARY_ALL)
    public ServiceStatus getStatus() {
        return ServiceStatus.builder()
                .serverPort(property.getServerPort())
                .isRunning(isRunning)
                .nodeInfo(clusterService.getSelf())
                .errorMsg(lastError)
                .build();
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        AtomicLong lastSend = new AtomicLong(System.currentTimeMillis());
        PoolUtils.submitInitTask(() -> {
            try {
                start();
            } catch (Exception err) {
                log.error("{}服务启动失败", LOG_PREFIX, err);
            }

            configService.addConfigSetListener(state -> {
                // 监听静态部署服务配置参数发生变化，若发生变化，则通过消息广播给集群所有实例重启服务
                if(state.getKey().startsWith("static-publish.")) {
                    long now = System.currentTimeMillis();
                    // 防抖5s触发
                    if (now - lastSend.get() > 5000) {
                        mqService.sendBroadcast(MQ_TOPIC_PROPERTY_CHANGE, "");
                        lastSend.set(now);
                    }
                }
            });

            mqService.subscribeBroadcast(MQ_TOPIC_PROPERTY_CHANGE, msg -> {
                try {
                    log.info("{}配置发生变更，服务重启中...", LOG_PREFIX);
                    if (isRunning()) {
                        stop();
                    }
                    start();
                    log.info("{}服务重启完成", LOG_PREFIX);
                } catch (Throwable err) {
                    log.error("{}配置变更后，服务启动失败", LOG_PREFIX, err);
                }
            });
        });

    }
}
