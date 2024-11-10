package com.xiaotao.saltedfishcloud.init;

import com.xiaotao.saltedfishcloud.constant.SysConfigName;
import com.xiaotao.saltedfishcloud.service.config.version.Version;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;

@Slf4j
public class VersionCheckInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private boolean isTableNotExist(BadSqlGrammarException e) {
        int maxDepth = 10;
        Throwable throwable = e;
        do {
            if (throwable.getMessage() != null && throwable.getMessage().contains("doesn't exist")) {
                return true;
            }
            throwable = throwable.getCause();
            maxDepth--;
            if (maxDepth < 0) {
                log.error("检查版本出错，SQL异常嵌套超过了10层", e);
                return false;
            }
        } while (throwable != null);
        return false;
    }

    @Override
    public void initialize(@NotNull ConfigurableApplicationContext applicationContext) {
        applicationContext.getBeanFactory().addBeanPostProcessor(new BeanPostProcessor() {

            private String getVersion(DataSource dataSource) {
                try {
                    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
                    List<String> res = jdbcTemplate.query("SELECT `value` FROM config WHERE `key` = '" + SysConfigName.Common.VERSION + "'", (rs, rowNum) -> rs.getString(1));
                    if (!res.isEmpty()) {
                        return res.get(0);
                    }
                    res = jdbcTemplate.query("SELECT `value` FROM config WHERE `key` = '" + SysConfigName.OLD_VERSION + "'", (rs, rowNum) -> rs.getString(1));
                    if (!res.isEmpty()) {
                        return res.get(0);
                    }
                } catch (BadSqlGrammarException e) {
                    // 新初始化的系统没有表，忽略
                    if (isTableNotExist(e)){
                        return null;
                    }
                    throw e;
                }
                return null;
            }
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (!(bean instanceof DataSource)) {
                    return bean;
                }
                String version = getVersion((DataSource) bean);
                if (version == null) {
                    return bean;
                }
                if (Version.valueOf(version).isLessThen(Version.valueOf("2.8.0"))) {
                    throw new IllegalArgumentException("上次运行的程序版本低于为" + version +"，低于2.8.0的版本无法直接升级到当前版本。请先升级到2.8.0，升级完成后再升级该版本。");
                }
                return bean;
            }
        });
    }
}
