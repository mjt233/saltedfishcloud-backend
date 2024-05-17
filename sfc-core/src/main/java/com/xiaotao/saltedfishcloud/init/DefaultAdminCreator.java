package com.xiaotao.saltedfishcloud.init;

import com.xiaotao.saltedfishcloud.dao.mybatis.UserDao;
import com.xiaotao.saltedfishcloud.model.po.User;
import com.xiaotao.saltedfishcloud.service.user.UserType;
import com.xiaotao.saltedfishcloud.utils.identifier.IdUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
@Slf4j
@Order(3)
public class DefaultAdminCreator  implements ApplicationRunner {
    @Resource
    private UserDao userDao;
    @Resource
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        int cnt = userDao.getUserCount();
        User admin;
        log.info("当前系统存在用户数：" + cnt);
        if (cnt == 0 || (admin = userDao.getUserByUser("admin")) == null ) {
            userDao.addUser("admin", passwordEncoder.encode("admin666"), "", UserType.ADMIN, IdUtil.getId());
            log.warn("创建初始管理员用户：admin 密码：admin666，建议及时修改密码");
        } else if (admin.getType() != User.TYPE_ADMIN) {
            userDao.grant(admin.getId(), User.TYPE_ADMIN);
            log.warn("已修复admin权限");
        }
    }
}
