package com.xiaotao.saltedfishcloud.controller.user;

import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.exception.HasResultException;
import com.xiaotao.saltedfishcloud.exception.UserNoExistException;
import com.xiaotao.saltedfishcloud.po.User;
import com.xiaotao.saltedfishcloud.service.user.UserService;
import com.xiaotao.saltedfishcloud.service.user.UserType;
import com.xiaotao.saltedfishcloud.po.JsonResult;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.annotation.security.RolesAllowed;

@Controller
@RequestMapping(value = "/api")
@ResponseBody
public class UserController {
    @Resource
    UserService userService;

    /**
     * 获取用户基本信息
     */
    @GetMapping("user")
    public JsonResult getUserInfo() throws UserNoExistException {
        return JsonResult.getInstance(SecureUtils.getSpringSecurityUser());
    }

    /**
     * 仅限管理员：添加一个用户
     * @param user  用户名
     * @param passwd    原始密码（即密码原文）
     * @param type  用户类型，可选"admin"与"common"
     */
    @PostMapping("user")
    @RolesAllowed({"ADMIN"})
    public JsonResult addUser(@RequestParam("user") String user,
                              @RequestParam("passwd") String passwd,
                              @RequestParam("type") String type) {
        Integer userType;
        switch (type) {
            case "admin":userType = UserType.ADMIN;break;
            case "common":userType = UserType.COMMON;break;
            default:
                return JsonResult.getInstance(400, null, "无效的用户类型");
        }
        userService.addUser(user, passwd, userType);
        return JsonResult.getInstance();
    }

    /**
     * 用户自主注册账号
     * @param user  用户名
     * @param rawPassword   原始密码（即密码原文）
     * @param regCode   注册邀请码
     */
    @PostMapping("regUser")
    public JsonResult regUser(@RequestParam("user") String user,
                              @RequestParam("passwd") String rawPassword,
                              @RequestParam("regcode") String regCode
                              ) throws HasResultException {
        if (!regCode.equals(DiskConfig.REG_CODE)) {
            throw new HasResultException("注册码不正确");
        }
        userService.addUser(user, rawPassword, User.TYPE_COMMON);
        return JsonResult.getInstance();
    }

}
