package com.sfc.webshell.upgrade;

import com.xiaotao.saltedfishcloud.annotations.update.InitAction;
import com.xiaotao.saltedfishcloud.annotations.update.Updater;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@Updater("web-shell")
@Component
public class WebShellUpdater {
    @Autowired
    private DataSource dataSource;

    @InitAction
    public void init() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(
                    connection,
                    new ClassPathResource("webshell-sql/init.sql", this.getClass().getClassLoader())
            );
        }
    }
}
