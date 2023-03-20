package com.sfc.quickshare.update;

import com.xiaotao.saltedfishcloud.annotations.update.InitAction;
import com.xiaotao.saltedfishcloud.annotations.update.Updater;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@Updater("quick-share")
@Component
@Slf4j
public class QuickShareUpdater {
    @Autowired
    private DataSource dataSource;

    @InitAction
    public void init() {
        try (Connection connection = dataSource.getConnection()) {
            log.info("快速分享插件初始化");
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("quick-share-sql/init.sql", this.getClass().getClassLoader()));
        } catch (SQLException e) {
            log.error("快速分享插件初始化失败", e);
        }
    }
}
