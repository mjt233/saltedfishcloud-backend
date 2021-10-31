package com.xiaotao.saltedfishcloud.init;

import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.dao.mybatis.ConfigDao;
import com.xiaotao.saltedfishcloud.service.config.ConfigName;
import com.xiaotao.saltedfishcloud.service.config.version.Version;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;

@Component
@Order(3)
@Slf4j
public class DatabaseUpdater implements ApplicationRunner {
    private final Connection conn;
    private final ConfigDao configDao;
    private final Version lastVersion;

    public DatabaseUpdater(DataSource dataSource, ConfigDao configDao) throws SQLException {
        conn = dataSource.getConnection();
        Version v;
        try {
            v = Version.load(configDao.getConfigure(ConfigName.VERSION));
        } catch (Exception e) {
            v = DiskConfig.VERSION;
        }
        lastVersion = v;
        this.configDao = configDao;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        tryExecute("1.3.0-SNAPSHOT");
        tryExecute("1.3.0.1-SNAPSHOT");
        tryExecute("1.3.4-SNAPSHOT");
        conn.close();
        configDao.setConfigure(ConfigName.VERSION, DiskConfig.VERSION.toString());
    }

    /**
     * 尝试执行数据表版本更新脚本，当上次运行的版本小于给定的version时，将执行对应version的数据表更新脚本
     * @param version 数据表更新脚本版本
     */
    private void tryExecute(String version) {
        var targetVersion = Version.load(version);
        if (lastVersion.isLessThen(targetVersion)) {
            log.info("[数据表更新]版本：" + targetVersion.toString());
            execute(version);
        }
    }

    /**
     * 通过数据库连接执行resources/sql/目录下的{name}.sql文件
     * @param name 文件名（不带后缀）
     */
    private void execute(String name) {
        ClassPathResource resource = new ClassPathResource("sql/" + name + ".sql");
        ScriptUtils.executeSqlScript(conn, resource);
    }
}
