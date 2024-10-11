package com.xiaotao.saltedfishcloud.init;

import com.xiaotao.saltedfishcloud.common.update.VersionUpdateHandler;
import com.xiaotao.saltedfishcloud.common.update.VersionUpdateManager;
import com.xiaotao.saltedfishcloud.config.SysProperties;
import com.xiaotao.saltedfishcloud.dao.mybatis.ConfigDao;
import com.xiaotao.saltedfishcloud.ext.PluginManager;
import com.xiaotao.saltedfishcloud.model.PluginInfo;
import com.xiaotao.saltedfishcloud.service.config.ConfigService;
import com.xiaotao.saltedfishcloud.constant.SysConfigName;
import com.xiaotao.saltedfishcloud.service.config.version.Version;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.Connection;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
@Order(2)
@Slf4j
public class DatabaseUpdater implements ApplicationRunner {
    private final ConfigDao configDao;
    private final DataSource dataSource;
    private final Version lastVersion;
    private final ResourceLoader resourceLoader;
    private final SysProperties sysProperties;
    private final VersionUpdateManager updateManager;

    @Autowired
    private ConfigService configService;

    @Autowired
    private PluginManager pluginManager;

    /**
     * SQL版本更新脚本资源类，用于从一个SQL版本更新脚本文件Resource中解析版本信息
     */
    @Getter
    private static class SQLVersionResource implements Comparable<SQLVersionResource> {
        private final Resource resource;
        private final Version version;
        public SQLVersionResource(Resource resource) {
            this.resource = resource;
            String name = resource.getFilename();
            assert name != null;
            this.version =  Version.valueOf(name.substring(0, name.length() - 4));
        }

        public static SQLVersionResource[] valueOf(Resource ... resources) {
            SQLVersionResource[] arr = new SQLVersionResource[resources.length];
            for (int i = 0; i < resources.length; i++) {
                arr[i] = new SQLVersionResource(resources[i]);
            }
            return arr;
        }

        @Override
        public int compareTo(SQLVersionResource o) {
            return o.getVersion().compareTo(this.version);
        }

        @Override
        public String toString() {
            return version.toString();
        }
    }

    public DatabaseUpdater(DataSource dataSource,
                           ConfigDao configDao,
                           ResourceLoader resourceLoader,
                           SysProperties sysProperties,
                           VersionUpdateManager updateManager
    ) throws IOException {
        Version v;
        try {
            String versionRecord = configDao.getConfigure(SysConfigName.Common.VERSION);
            if (versionRecord == null) {
                versionRecord = configDao.getConfigure(SysConfigName.OLD_VERSION);
            }
            if (versionRecord != null) {
                v = Version.valueOf(versionRecord);
            } else {
                v = sysProperties.getVersion();
            }
        } catch (Exception e) {
            v = sysProperties.getVersion();
        }
        lastVersion = v;
        this.dataSource = dataSource;
        this.configDao = configDao;
        this.resourceLoader = resourceLoader;
        this.sysProperties = sysProperties;
        this.updateManager = updateManager;
        this.registerSQLUpdateHandler();
    }

    /**
     * 注册SQL更新脚本操作器
     */
    private void registerSQLUpdateHandler() throws IOException {
        // 读取资源目录/sql下的所有版本更新sql文件
        try {
            Resource[] sqls = Arrays.stream(ResourcePatternUtils
                            .getResourcePatternResolver(resourceLoader)
                            .getResources("classpath:/sql/*.*.*.sql"))
                    .filter(e -> !Objects.requireNonNull(e.getFilename()).endsWith("no-auto.sql"))
                    .toArray(Resource[]::new);
            SQLVersionResource[] resources = SQLVersionResource.valueOf(sqls);
            Arrays.sort(resources);
            for (SQLVersionResource resource : resources) {
                this.updateManager.registerUpdateHandler(new VersionUpdateHandler() {
                    @Override
                    public void update(Version from, Version to) throws Exception {
                        try (Connection connection = dataSource.getConnection()) {
                            ScriptUtils.executeSqlScript(connection, resource.getResource());
                        }
                    }

                    @Override
                    public Version getUpdateVersion() {
                        return resource.getVersion();
                    }

                    @Override
                    public String getMessage() {
                        return "数据库结构更新：" + resource.getVersion();
                    }
                });
            }
        } catch (FileNotFoundException ignored) {
            // 当不存在sql目录时就会报这个 忽略即可
        }
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        handleGlobalUpdate();
        handlePluginUpdate();
    }

    /**
     * 获取插件更新作用域的配置key
     * @param pluginName    插件name
     */
    private String getPluginUpdateScopeKey(String pluginName) {
        return "plugin." + pluginName + ".version";
    }

    /**
     * 执行插件作用域的版本更新
     */
    private void handlePluginUpdate() throws Exception {
        // 筛选出除内置插件外的插件
        List<PluginInfo> pluginList = pluginManager.listAllPlugin().stream().filter(e -> !"sys".equals(e.getName())).collect(Collectors.toList());

        // 组装插件名称用于查询记录的插件上个版本
        String pluginNames = pluginList.stream()
                .map(e -> "'" + this.getPluginUpdateScopeKey(e.getName()) + "'")
                .collect(Collectors.joining(","));

        if (!StringUtils.hasText(pluginNames)) {
            return;
        }
        pluginNames = "(" + pluginNames + ")";
        Map<String, String> pluginLastVersionMap = configService.listConfig("IN", pluginNames);

        for (PluginInfo pluginInfo : pluginList) {
            // 筛选出具有上次运行版本记录的插件
            String versionKey = this.getPluginUpdateScopeKey(pluginInfo.getName());
            String lastPluginVersionStr = pluginLastVersionMap.get(versionKey);
            Version nowVersion = Version.valueOf(pluginInfo.getVersion());

            if (lastPluginVersionStr == null) {
                List<VersionUpdateHandler> initHandlers = updateManager.getInitHandlerList(pluginInfo.getName());
                for (VersionUpdateHandler handler : initHandlers) {
                    handler.update(null, nowVersion);
                }
                configService.setConfig(versionKey, pluginInfo.getVersion());
                continue;
            }
            Version lastVersion = Version.valueOf(lastPluginVersionStr);

            // 筛选出需要执行更新的操作器
            List<VersionUpdateHandler> updateHandlerList = updateManager.getNeedUpdateHandlerList(pluginInfo.getName(), lastVersion);
            if (updateHandlerList == null || updateHandlerList.isEmpty()) {
                configService.setConfig(versionKey, pluginInfo.getVersion());
                continue;
            }

            // 执行更新
            try {
                for (VersionUpdateHandler handler : updateHandlerList) {
                    log.info("{}执行{}的{}更新", "[插件更新]", handler.getScope(), handler.getUpdateVersion());
                    handler.update(lastVersion, nowVersion);
                }
                configService.setConfig(versionKey, pluginInfo.getVersion());
            } catch (Exception e) {
                updateHandlerList.forEach(handler -> handler.rollback(lastVersion, nowVersion));
                throw e;
            }

        }

    }

    /**
     * 执行全局作用域的版本更新
     */
    private void handleGlobalUpdate() throws Exception {
        try {
            for (VersionUpdateHandler updateHandler : updateManager.getNeedUpdateHandlerList(null, lastVersion)) {
                if (log.isInfoEnabled()) {
                    log.info("[版本更新]======= 执行更新程序 =======[{}-{}]{}",updateHandler.getScope(), updateHandler.getUpdateVersion(), updateHandler.getMessage());
                }
                updateHandler.update(lastVersion, sysProperties.getVersion());
            }
        } catch (Exception e) {
            log.error("更新出错：", e);
            for (VersionUpdateHandler updateHandler : updateManager.getNeedUpdateHandlerList(null, lastVersion)) {
                updateHandler.rollback(lastVersion, sysProperties.getVersion());
            }
            throw e;
        }
        configDao.setConfigure(SysConfigName.Common.VERSION, sysProperties.getVersion().toString());
    }

}
