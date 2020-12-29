package com.xiaotao.saltedfishcloud.controller.user;

import com.xiaotao.saltedfishcloud.exception.UserNoExistException;
import com.xiaotao.saltedfishcloud.po.User;
import com.xiaotao.saltedfishcloud.service.UserService;
import com.xiaotao.saltedfishcloud.service.UserType;
import com.xiaotao.saltedfishcloud.utils.JsonResult;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.Resource;
import javax.annotation.security.RolesAllowed;

@Controller
@RequestMapping(value = "/api/User", method = RequestMethod.POST)
@ResponseBody
public class UserController {
    @Resource
    UserService userService;

//    @RequestMapping("login")
//    public JsonResult login(@RequestParam("user") String userName, @RequestParam("passwd") String passwd,
//                            HttpServletResponse response, HttpServletRequest request) throws HasResultException {
//        String pwd = SecureUtils.getPassswd(passwd);
//        User user = userService.getUserByUser(userName);
//        if ( !user.getPwd().equals(pwd)) {
//            throw new HasResultException(-1, "密码错误");
//        }
//        userService.updateLoginDate(user.getId());
//        return JsonResult.getInstance(user.getToken());
//    }

    @RequestMapping("test")
    public JsonResult test() {
        throw new RuntimeException();
    }

    @RequestMapping("getUserInfo")
    public JsonResult getUserInfo() throws UserNoExistException {
        String name = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userService.getUserByUser(name);
        user.setPwd(null);
        user.setToken(null);
        return JsonResult.getInstance(user);
    }

    @RequestMapping("getAdminInfo")
    @RolesAllowed({"ADMIN"})
    public JsonResult getAdminInfo() throws UserNoExistException {
        return getUserInfo();
    }


    @RequestMapping("/admin/addUser")
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
