package com.xiaotao.saltedfishcloud.init;

import com.xiaotao.saltedfishcloud.annotations.update.RollbackAction;
import com.xiaotao.saltedfishcloud.annotations.update.UpdateAction;
import com.xiaotao.saltedfishcloud.annotations.update.Updater;
import com.xiaotao.saltedfishcloud.config.SysProperties;
import com.xiaotao.saltedfishcloud.model.po.ProxyInfo;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.FileInfoService;
import com.xiaotao.saltedfishcloud.service.file.StoreServiceFactory;
import com.xiaotao.saltedfishcloud.service.file.UserCustomStoreService;
import com.xiaotao.saltedfishcloud.service.file.store.Storage;
import com.xiaotao.saltedfishcloud.utils.MigrateUtils;
import com.xiaotao.saltedfishcloud.utils.ObjectUtils;
import com.xiaotao.saltedfishcloud.utils.SpringContextUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import com.xiaotao.saltedfishcloud.utils.identifier.IdUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 系统核心模块版本更新处理器
 */
@Component
@Updater
@Slf4j
public class SystemUpdater {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private FileInfoService fileInfoService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private void executeSqlResource(String sqlFileClassPath) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(sqlFileClassPath));
        }
    }

    /**
     * 迁移 3.1.2 版本调整后的临时数据目录。
     */
    @UpdateAction("3.1.2")
    public void update3_1_2() throws IOException {
        // 由于移除了 {@code TempStoreService}，原本位于临时目录中的第三方平台头像缓存需要迁移到附属存储目录中。
        MigrateUtils.moveDirectory("temp/thirdPlatformAvatar", "attach/third_platform_avatar");
        MigrateUtils.moveDirectory("temp/quick_share", "attach/quick_share");

        // 桌面组件公共留言板 comment-board 组件名称调整为 public-comment-board，原 comment-board 组件作为通用评论/留言板实现。
        jdbcTemplate.execute("UPDATE desktop_component_config SET name = 'public-comment-board' WHERE name = 'comment-board'");
    }

    @UpdateAction("3.1.0")
    public void update3_1_0() throws IOException {
        // 迁移头像数据
        // 升级到 3.1.0 由于头像存储采用了统一的 AttachStorage附属存储机制 和 单独的 UserCustomStore接口，需要将数据从旧的存储区中迁移到新的存储区
        Storage storageProvider = SpringContextUtils.getContext().getBean(StoreServiceFactory.class).getService().getStorageProvider();
        SysProperties sysProperties = SpringContextUtils.getContext().getBean(SysProperties.class);
        UserCustomStoreService userCustomStoreService = SpringContextUtils.getContext().getBean(UserCustomStoreService.class);

        String oldUserProfilePath = StringUtils.appendPath(sysProperties.getStore().getRoot(), "user_profile");
        storageProvider.listFiles(oldUserProfilePath)
                .stream()
                .filter(FileInfo::isDir)
                .forEach(uidDir -> {
                    String uid = uidDir.getName();
                    try {
                        storageProvider.listFiles(oldUserProfilePath + "/" + uidDir.getName())
                                .stream()
                                .filter(avatarFile -> avatarFile.getName().startsWith("avatar."))
                                .findAny()
                                .ifPresent(avatarFile -> {
                                    try {
                                        Resource avatarResource = storageProvider.getResource(oldUserProfilePath + "/" + uidDir.getName() + "/" + avatarFile.getName());
                                        if (avatarResource != null) {
                                            userCustomStoreService.saveAvatar(Long.parseLong(uid), avatarResource);
                                            log.info("迁移用户 {} 头像成功", uid);
                                        }
                                    } catch (IOException e) {
                                        log.error("迁移用户 {} 头像失败", uid, e);
                                    }
                                });
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

        // 删除旧目录
        storageProvider.delete(oldUserProfilePath);

        // 迁移缩略图缓存
        MigrateUtils.moveDirectory("temp/thumbnail", "attach/thumbnail");
    }


    @RollbackAction("2.7.0")
    public void rollback2_7_0() throws SQLException {
        this.executeSqlResource("sql/2.7.0.rollback.no-auto.sql");
        log.warn("2.7.0更新出错，但是表结构回滚成功");
    }

    @UpdateAction("2.7.0")
    public void update2_7_0() throws SQLException {
        // 先执行修改表结构的脚本
        long begin = System.currentTimeMillis();
        log.info("======== 开始执行2.7.0 文件表file_table 表结构更新 ========");
        log.info("======== 执行结构调整脚本... ========");
        this.executeSqlResource("sql/2.7.0.no-auto.sql");

        log.info("======== 查询原数据... ========");
        List<FileInfo> fileInfoList = jdbcTemplate.queryForList("SELECT * FROM file_table")
                .stream()
                .map(mapRes -> {
                    HashMap<String, Object> newMap = new HashMap<>();
                    mapRes.forEach((field, val) -> newMap.put(StringUtils.underToCamel(field), val));
                    return ObjectUtils.mapToBean(newMap, FileInfo.class);
                })
                .collect(Collectors.toList());
        log.info("======== 共{}条数据 ========", fileInfoList.size());

        DefaultTransactionDefinition transactionDefinition = new DefaultTransactionDefinition();
        TransactionStatus transaction = transactionManager.getTransaction(transactionDefinition);

        try {
            // 给文件列表数据赋值ID
            log.info("======== 清空原数据... ========");
            jdbcTemplate.execute("DELETE FROM file_table");
            log.info("======== 更新新数据... ========");
            fileInfoService.batchInsert(fileInfoList);
            log.info("======== 提交事务... ========");
            transactionManager.commit(transaction);

            // 设置主键字段
            log.info("======== 设置主键... ========");
            jdbcTemplate.execute("ALTER TABLE file_table ADD PRIMARY KEY (`id`)");
            log.info("======== 更新完成，共{}条数据，耗时：{}ms ========", fileInfoList.size(), System.currentTimeMillis() - begin);
        } catch (Throwable err) {
            try {
                transactionManager.rollback(transaction);
            } catch (Throwable ignore) {}
            log.error("file_table表结构更新出错: ", err);
            throw err;
        }
    }

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
