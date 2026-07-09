package com.sfc.ext.webdav.core;

import com.sfc.ext.webdav.model.property.WebDavProperty;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import io.milton.http.annotated.AnnotationResourceFactory;
import io.milton.servlet.MiltonFilter;
import jakarta.servlet.ServletContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.webresources.StandardRoot;
import org.apache.tomcat.JarScanFilter;
import org.apache.tomcat.JarScanType;
import org.apache.tomcat.JarScanner;
import org.apache.tomcat.JarScannerCallback;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
public class WebDavManagerService {
    private final static String LOG_PREFIX = "[WebDAV]";
    private final WebDavProperty webDavProperty;

    private Tomcat tomcatInst;

    public boolean isRunning() {
        return tomcatInst != null;
    }

    public void start() throws IOException {
        if (this.tomcatInst != null) {
            log.warn("{} 已存在未关闭的 WebDAV Tomcat 实例", LOG_PREFIX);
            this.stop();
        }

        log.info("{} 启动 WebDAV 服务器中...", LOG_PREFIX);

        // 创建一个 Tomcat 实例用于 Milton 的 WebDAV服务
        Tomcat tomcat = new Tomcat();
        Connector connector = new Connector();
        connector.setPort(Optional.ofNullable(webDavProperty.getListenPort()).orElse(8086));
        Optional.ofNullable(webDavProperty.getListenIp())
                .filter(StringUtils::hasText)
                .ifPresent(ip -> connector.setProperty("address", ip));
        tomcat.setAddDefaultWebXmlToWebapp(false);
        tomcat.setConnector(connector);

        Path tomcatTmpPath = PathUtils.getAndCreateTempDirPath("webdav");
        Context ctx = tomcat.addWebapp("", tomcatTmpPath.toAbsolutePath().toString());
        Tomcat.addServlet(ctx, "default", new DefaultServlet());
        ctx.addServletMappingDecoded("/", "default");
        ctx.setReloadable(false);
        // 不需要扫描任何 jar
        ctx.setJarScanner(new JarScanner() {
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
        });
        ctx.setResources(new StandardRoot());
        ctx.setParentClassLoader(this.getClass().getClassLoader());
        ctx.addLifecycleListener(l -> {
            switch (l.getLifecycle().getState()) {
                case FAILED:
                    log.warn("{} WebDAV Tomcat 意外退出", l.getLifecycle().getState().getLifecycleEvent());
                case STOPPED:
                    this.stop();
                    log.info("{} WebDAV Tomcat 已关闭", LOG_PREFIX);
                    break;
                default:
                    break;
            }
        });

        // 配置 Milton WebDAV过滤器
        FilterDef filterDef = new FilterDef();
        filterDef.setFilter(new MiltonFilter());
        filterDef.setFilterName("MiltonFilter");
        filterDef.addInitParameter("resource.factory.class", AnnotationResourceFactory.class.getName());
        filterDef.addInitParameter("milton.configurator", SfcMiltonConfigurator.class.getName());
        filterDef.addInitParameter("controllerPackagesToScan", "com.sfc.ext.webdav.controller");
        ctx.addFilterDef(filterDef);
        FilterMap filterMap = new FilterMap();
        filterMap.setFilterName("MiltonFilter");
        filterMap.addURLPattern("/*");
        ctx.addFilterMap(filterMap);

        // 启动 Tomcat
        try {
            tomcat.getConnector();
            tomcat.start();
        } catch (LifecycleException e) {
            throw new RuntimeException(e);
        }
        this.tomcatInst = tomcat;

        log.info("{} WebDAV 服务启动成功", LOG_PREFIX);
    }

    public void stop() {
        if (this.tomcatInst == null) {
            log.warn("{} 不存在已启动的 WebDAV Tomcat 实例", LOG_PREFIX);
            return;
        }
        try {
            this.tomcatInst.stop();
        } catch (LifecycleException e) {
            throw new RuntimeException(e);
        }
        this.tomcatInst = null;
    }
}
