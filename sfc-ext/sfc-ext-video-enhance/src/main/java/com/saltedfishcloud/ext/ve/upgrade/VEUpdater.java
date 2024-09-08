package com.saltedfishcloud.ext.ve.upgrade;

import com.xiaotao.saltedfishcloud.annotations.update.InitAction;
import com.xiaotao.saltedfishcloud.annotations.update.UpdateAction;
import com.xiaotao.saltedfishcloud.annotations.update.Updater;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.SQLException;

@Updater("video-enhance")
@Component
@Slf4j
public class VEUpdater {
    @Autowired
    private DataSource dataSource;

    private void executeScript(String name) throws SQLException {
        ScriptUtils.executeSqlScript(dataSource.getConnection(), new ClassPathResource("ve-sql/" + name + ".sql", this.getClass().getClassLoader()));
    }

    @UpdateAction("1.1.1")
    public void createTaskTable111() throws SQLException {

        executeScript("1.1.1");
    }

    @UpdateAction("1.2.0")
    public void createTaskTable120() throws SQLException {

        executeScript("1.2.0");
    }
}
