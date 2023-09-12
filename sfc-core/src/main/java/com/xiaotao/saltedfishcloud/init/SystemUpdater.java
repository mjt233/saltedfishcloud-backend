package com.xiaotao.saltedfishcloud.init;

import com.xiaotao.saltedfishcloud.annotations.update.UpdateAction;
import com.xiaotao.saltedfishcloud.annotations.update.Updater;
import com.xiaotao.saltedfishcloud.model.po.ProxyInfo;
import com.xiaotao.saltedfishcloud.model.po.User;
import com.xiaotao.saltedfishcloud.utils.identifier.IdUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * 系统核心模块版本更新处理器
 */
@Component
@Updater
public class SystemUpdater {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @UpdateAction("2.6.6")
    public void update2_6_6() {
        /*
            2.6.6版本更新主要内容：
            代理信息配置支持用户自行配置代理节点和连通性测试URL
         */

        // 先对新增字段，并赋值初始值
        Date now = new Date();
        jdbcTemplate.execute(
                "ALTER TABLE `proxy` ADD COLUMN `id`  bigint NOT NULL COMMENT '主键' FIRST," +
                "ADD COLUMN `uid`  bigint NULL DEFAULT 0 COMMENT '创建用户id'," +
                "ADD COLUMN `test_url`  varchar(255) NULL," +
                "ADD COLUMN `create_at`  datetime NULL," +
                "ADD COLUMN `update_at`  datetime NULL"
        );
        jdbcTemplate.query("SELECT * FROM proxy", new BeanPropertyRowMapper<>(ProxyInfo.class))
                .forEach(proxyInfo -> jdbcTemplate.update(
                        "UPDATE proxy SET id = ?, uid = 0, create_at = ?, update_at = ? WHERE name = ?",
                        IdUtil.getId(), now, now, proxyInfo.getName())
                );

        // 重新构建主键字段
        jdbcTemplate.execute("ALTER TABLE `proxy` DROP PRIMARY KEY, ADD PRIMARY KEY (`id`)");

        // 构建索引
        jdbcTemplate.execute("ALTER TABLE `proxy` ADD INDEX `i_uid` (`uid`, `name`) USING BTREE");

    }
}
