package com.xiaotao.saltedfishcloud.controller.user;

import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.exception.HasResultException;
import com.xiaotao.saltedfishcloud.exception.UserNoExistException;
import com.xiaotao.saltedfishcloud.po.User;
import com.xiaotao.saltedfishcloud.service.user.UserService;
import com.xiaotao.saltedfishcloud.service.user.UserType;
import com.xiaotao.saltedfishcloud.utils.JsonResult;
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

    @GetMapping("user")
    public JsonResult getUserInfo() throws UserNoExistException {
        String name = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userService.getUserByUser(name);
        user.setPwd(null);
        user.setToken(null);
        return JsonResult.getInstance(user);
    }

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
