package com.xiaotao.saltedfishcloud.init;

import com.xiaotao.saltedfishcloud.common.update.VersionUpdateHandler;
import com.xiaotao.saltedfishcloud.common.update.VersionUpdateManager;
import com.xiaotao.saltedfishcloud.config.SysProperties;
import com.xiaotao.saltedfishcloud.dao.mybatis.ConfigDao;
import com.xiaotao.saltedfishcloud.service.config.SysConfigName;
import com.xiaotao.saltedfishcloud.service.config.version.Version;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.util.Arrays;

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
        Resource[] sqls = ResourcePatternUtils
                .getResourcePatternResolver(resourceLoader)
                .getResources("classpath:/sql/*.*.*.sql");
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
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try {
            for (VersionUpdateHandler updateHandler : updateManager.getNeedUpdateHandlerList(null, lastVersion)) {
                if (log.isInfoEnabled()) {
                    log.info("[版本更新][{}]{}", updateHandler.getUpdateVersion(), updateHandler.getMessage());
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
