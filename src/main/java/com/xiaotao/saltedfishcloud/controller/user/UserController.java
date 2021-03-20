package com.xiaotao.saltedfishcloud.controller.user;

import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.dao.UserDao;
import com.xiaotao.saltedfishcloud.exception.HasResultException;
import com.xiaotao.saltedfishcloud.exception.UserNoExistException;
import com.xiaotao.saltedfishcloud.po.JsonResult;
import com.xiaotao.saltedfishcloud.po.QuotaInfo;
import com.xiaotao.saltedfishcloud.po.User;
import com.xiaotao.saltedfishcloud.service.file.FileService;
import com.xiaotao.saltedfishcloud.service.user.UserService;
import com.xiaotao.saltedfishcloud.service.user.UserType;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.annotation.security.RolesAllowed;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;

@Controller
@RequestMapping(value = "/api")
@ResponseBody
@Slf4j
public class UserController {
    @Resource
    UserService userService;

    @Resource
    FileService fileService;

    @Resource
    UserDao userDao;
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

    /**
     * 上传用户头像
     * @param file  头像文件
     */
    @PostMapping("uploadAvatar")
    public JsonResult uploadAvatar(@RequestParam("file") MultipartFile file,
                                   @RequestAttribute User user) throws IOException {
        userService.setAvatar(user.getUsername(), file);
        return JsonResult.getInstance();
    }

    /**
     * 获取用户头像信息，直接响应头像文件
     * @param username  用户名
     */
    @GetMapping({
            "getAvatar/{username}",
            "getAvatar"
    })
    public ResponseEntity<org.springframework.core.io.Resource>
                getAvatar(HttpServletResponse response, @PathVariable(required = false) String username) throws IOException {
        try {
            String profilePath = username == null ? DiskConfig.getLoginUserProfileRoot() : DiskConfig.getUserProfileRoot(username);
            File[] avatars = new File(profilePath).listFiles(pathname -> pathname.getName().contains("avatar"));

            // 数组越界，空指针操作均视为头像不存在
            return fileService.sendFile(avatars[0].getPath());
        } catch (Exception e) {
            response.sendRedirect("/api/static/static/defaultAvatar.png");
            return null;
        }
    }

    /**
     * 获取用户空间配额使用情况
     */
    @GetMapping("quotaUsed")
    public JsonResult getQuotaUsed() {
        QuotaInfo used = userDao.getUserQuotaUsed(SecureUtils.getSpringSecurityUser().getId());
        return JsonResult.getInstance(used);
    }

}
