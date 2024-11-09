package com.xiaotao.saltedfishcloud.init;

import com.xiaotao.saltedfishcloud.utils.DBUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import jakarta.annotation.Resource;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * 数据库初始化器，在配置服务 {@link com.xiaotao.saltedfishcloud.service.config.ConfigService} Bean初始化完成后执行
 */
@Configuration
@Slf4j
public class DatabaseInitializer {
    @Resource
    private DataSource dataSource;

    /**
     * 判断数据库中是否存在数据表<br>
     * 执行后数据库连接不会被关闭
     * @author xiaotao mjt233@qq.com
     * @param conn  数据库连接
     */
    private boolean isTableExist(Connection conn) throws SQLException {
        return !DBUtils.isDBEmpty(conn);
    }

    public void init() throws SQLException {
        try(Connection con = dataSource.getConnection()) {
            // 若数据库无数据表则先初始化
            if (!isTableExist(con)) {
                log.info("[数据库]正在初始化数据表...");
                org.springframework.core.io.Resource resource = new ClassPathResource("sql/full.sql");
                ScriptUtils.executeSqlScript(con, resource);
                log.info("[数据库]数据表初始化完成（好耶）");
            }
        }
    }
}
