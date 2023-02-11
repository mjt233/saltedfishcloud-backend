package com.sfc.task.upgrade;

import com.xiaotao.saltedfishcloud.annotations.update.InitAction;
import com.xiaotao.saltedfishcloud.annotations.update.Updater;
import com.xiaotao.saltedfishcloud.common.update.VersionUpdateManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.SQLException;

@Updater("sfc-task")
@Component
@Slf4j
public class SfcTaskUpdater {

    @Autowired
    private DataSource dataSource;

    private void executeScript(String name) throws SQLException {
        ScriptUtils.executeSqlScript(dataSource.getConnection(), new ClassPathResource("task-sql/" + name + ".sql", this.getClass().getClassLoader()));
    }

    @InitAction
    public void init() throws SQLException {
        log.info("任务模块初始化");
        executeScript("init");
    }

}
