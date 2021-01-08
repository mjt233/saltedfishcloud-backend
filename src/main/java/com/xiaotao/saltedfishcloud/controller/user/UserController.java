package com.xiaotao.saltedfishcloud.controller.user;

import com.xiaotao.saltedfishcloud.exception.UserNoExistException;
import com.xiaotao.saltedfishcloud.po.User;
import com.xiaotao.saltedfishcloud.service.UserService;
import com.xiaotao.saltedfishcloud.service.UserType;
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

    @PutMapping("user")
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

}
