package com.sfc.nwt.update;


import com.xiaotao.saltedfishcloud.annotations.update.InitAction;
import com.xiaotao.saltedfishcloud.annotations.update.Updater;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@Updater("network-tools")
public class NwtUpdater {
    @Autowired
    private DataSource dataSource;

    @InitAction
    public void init() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("nwt-sql/init.sql", this.getClass().getClassLoader()));
        }
    }
}
