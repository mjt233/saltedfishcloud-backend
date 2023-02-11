package com.xiaotao.saltedfishcloud.download.upgrade;

import com.xiaotao.saltedfishcloud.annotations.update.InitAction;
import com.xiaotao.saltedfishcloud.annotations.update.Updater;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@Component
@Updater("offline-download")
public class DownloadUpdater {
    @Autowired
    private DataSource dataSource;

    @InitAction
    public void init() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("download-sql/init.sql"));
        }

    }
}
