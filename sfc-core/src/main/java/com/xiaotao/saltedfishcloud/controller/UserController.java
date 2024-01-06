package com.xiaotao.saltedfishcloud.controller;

import com.xiaotao.saltedfishcloud.annotations.AllowAnonymous;
import com.xiaotao.saltedfishcloud.config.SysRuntimeConfig;
import com.xiaotao.saltedfishcloud.dao.mybatis.UserDao;
import com.xiaotao.saltedfishcloud.dao.redis.TokenServiceImpl;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.exception.UserNoExistException;
import com.xiaotao.saltedfishcloud.model.CommonPageInfo;
import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.model.param.PageableRequest;
import com.xiaotao.saltedfishcloud.model.po.QuotaInfo;
import com.xiaotao.saltedfishcloud.model.po.User;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.service.user.UserService;
import com.xiaotao.saltedfishcloud.utils.*;
import com.xiaotao.saltedfishcloud.validator.annotations.UID;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.hibernate.validator.constraints.Length;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.security.RolesAllowed;
import javax.mail.MessagingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import javax.validation.constraints.Email;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping(UserController.PREFIX)
@ResponseBody
@Validated
@RequiredArgsConstructor
public class UserController {
    public static final String PREFIX = "/api/user";

    private final UserService userService;
    private final DiskFileSystemManager fileSystemFactory;
    private final UserDao userDao;
    private final TokenServiceImpl tokenDao;
    private final SysRuntimeConfig runtimeConfig;

    @ApiOperation("验证用户重置密码时输入的验证码是否正确")
    @PostMapping("/validResetPasswordEmailCode")
    @AllowAnonymous
    public JsonResult<Boolean> validResetPasswordEmailCode(@RequestParam("account") String account,
                                                  @RequestParam("code") String code) {
        return JsonResultImpl.getInstance(userService.validResetPasswordEmailCode(account, code));
    }

    @PostMapping("findBaseUserInfo")
    @AllowAnonymous
    public JsonResult<List<User>> findBaseUserInfo(@RequestBody List<Long> ids) {
        return JsonResultImpl.getInstance(userService.findBaseInfoByIds(ids));
    }

    /**
     * 获取新token
     * @param kick 是否使旧token失效（踢下线）
     */
    @PostMapping("/updateToken")
    public JsonResult<String> updateToken(@RequestParam(value = "kick", defaultValue = "true") boolean kick) {
        final User user = userService.getUserById(SecureUtils.getSpringSecurityUser().getId());
        if (kick) {
            tokenDao.cleanUserToken(user.getId());
        }
        String token = tokenDao.generateUserToken(user.getId());
        return JsonResultImpl.getInstance(token);
    }

    /**
     * 重置用户密码
     * @param account   用户用户名或用户邮箱
     * @param code      邮箱验证码
     * @param password  新密码
     */
    @PostMapping("/resetPassword")
    @AllowAnonymous
    public JsonResult<?> resetPassword(@RequestParam("account") String account,
                                    @RequestParam("code") String code,
                                    @RequestParam("password") @Length(min = 6) String password) {
        userService.resetPassword(account, code, password);
        return JsonResult.emptySuccess();
    }

    /**
     * 用户绑定新邮箱
     * @param email 新邮箱
     * @param originCode 旧邮箱验证码，可空
     * @param newCode  新邮箱验证码
     */
    @PostMapping("/newMail")
    public JsonResult<?> setEmail(@RequestParam("email") @Email String email,
                               @RequestParam(value = "originCode", required = false) String originCode,
                               @RequestParam("newCode") String newCode) {
        Long uid = SecureUtils.getSpringSecurityUser().getId();
        userService.bindEmail(uid, email, originCode, newCode);
        return JsonResult.emptySuccess();
    }

    /**
     * 发送新邮箱绑定验证码
     * @param email 新邮箱
     */
    @PostMapping("/sendBindEmail")
    public JsonResult<?> sendBindEmail(@RequestParam("email") @Email String email) throws MessagingException, UnsupportedEncodingException {
        Long uid = SecureUtils.getSpringSecurityUser().getId();
        userService.sendBindEmail(uid, email);
        return JsonResult.emptySuccess();
    }

    /**
     * 发送用于验证旧邮箱的邮箱验证码
     */
    @PostMapping("/sendVerifyEmail")
    public JsonResult<?> sendVerifyEmail() throws MessagingException, UnsupportedEncodingException {
        userService.sendVerifyEmail(SecureUtils.getSpringSecurityUser().getId());
        return JsonResult.emptySuccess();
    }

    /**
     * 验证旧邮箱
     * @param code 验证码
     */
    @PostMapping("/verifyEmail")
    public JsonResult<?> verifyEmail(@RequestParam("code") String code) throws MessagingException, UnsupportedEncodingException {
        userService.verifyEmail(SecureUtils.getSpringSecurityUser().getId(), code);
        return JsonResult.emptySuccess();
    }

    /**
     * 发送重置密码验证邮件
     */
    @PostMapping("/sendResetPasswordEmail")
    @AllowAnonymous
    public JsonResult<?> sendResetPasswordEmail(@RequestParam(value = "account") String account) throws MessagingException, UnsupportedEncodingException {
        userService.sendResetPasswordEmail(account);
        return JsonResult.emptySuccess();
    }

    /**
     * 获取允许的注册类型
     */
    @GetMapping("/regType")
    @AllowAnonymous
    public JsonResult<?> getRegType() {
        return JsonResultImpl.getInstance(new HashMap<String, Boolean>(){{
            put("email", runtimeConfig.isEnableEmailReg());
            put("regcode", runtimeConfig.isEnableRegCode());
        }});
    }

    /**
     * 获取用户基本信息，并刷新token有效期
     */
    @GetMapping
    public JsonResult<User> getUserInfo(HttpServletRequest request) throws UserNoExistException {
        User user = SecureUtils.getSpringSecurityUser();
        if (user == null) {
            throw new JsonException(401, "未登录");
        }
        tokenDao.setToken(user.getId(), request.getHeader(JwtUtils.AUTHORIZATION));
        return JsonResultImpl.getInstance(user);
    }


    /**
     * 发送注册验证码
     * @param email 邮件
     */
    @PostMapping("/regcode")
    @AllowAnonymous
    public JsonResult<?> sendRegCode(@Validated @NotBlank @RequestParam("email") @Email String email) {
        userService.sendRegEmail(email);
        return JsonResult.emptySuccess();
    }

    /**
     * 注册新账号，管理员可直接添加无需邀请码
     * @param user  用户名
     * @param rawPassword   原始密码（即密码原文）
     * @param regCode   注册邀请码
     */
    @PostMapping
    @AllowAnonymous
    public JsonResult<?> regUser(@RequestParam("user") String user,
                              @RequestParam("passwd") @Length(min = 6) String rawPassword,
                              @RequestParam(value = "regcode", defaultValue = "") String regCode,
                              @RequestParam("email") @Email String email,
                              @RequestParam(value = "type", defaultValue = "0") int type,
                              @RequestParam(value = "validEmail", defaultValue = "false") Boolean validEmail
                              ) throws JsonException {



        // 管理员直接添加，不受任何约束
        if (SecureUtils.getSpringSecurityUser() != null && SecureUtils.getSpringSecurityUser().getType() == User.TYPE_ADMIN) {
            userService.addUser(user, rawPassword, email, type == User.TYPE_ADMIN ? User.TYPE_ADMIN : User.TYPE_COMMON);
        } else {
            userService.addUser(user, rawPassword, email, regCode, validEmail);
        }
        return JsonResult.emptySuccess();
    }

    /**
     * 上传用户头像
     * @param file  头像文件
     */
    @PostMapping("avatar")
    public JsonResult<?> uploadAvatar(@RequestParam("file") MultipartFile file) throws IOException {
        fileSystemFactory.getMainFileSystem().saveAvatar(
                SecureUtils.getSpringSecurityUser().getId(),
                new MultipartFileResource(file)
        );
        return JsonResult.emptySuccess();
    }

    /**
     * 获取用户头像信息，直接响应头像文件
     * @param username  用户名
     */
    @GetMapping({
            "avatar/{username}",
            "avatar"
    })
    @AllowAnonymous
    public ResponseEntity<Resource>
                getAvatar(HttpServletResponse response,
                          @RequestParam(required = false) Long uid,
                          @PathVariable(required = false) String username) throws IOException {
        try {
            User currentUser = SecureUtils.getSpringSecurityUser();
            if (currentUser == null && username == null && uid == null) {
                response.sendRedirect("/api/static/defaultAvatar.png");
                return null;
            }
            Long finalUid = 0L;

            if (uid != null) {
                finalUid = uid;
            } else if (username != null) {
                User user = userService.getUserByUser(username);
                if (user != null) {
                    finalUid = user.getId();
                }
            } else {
                finalUid = currentUser.getId();
            }
            return ResourceUtils.wrapResource(fileSystemFactory.getMainFileSystem().getAvatar(finalUid));
        } catch (Exception e) {
            e.printStackTrace();
        }
        response.sendRedirect("/api/static/defaultAvatar.png");
        return null;
    }

    /**
     * 获取用户空间配额使用情况
     */
    @GetMapping("quota")
    public JsonResult<QuotaInfo> getQuotaUsed() {
        QuotaInfo used = userDao.getUserQuotaUsed(SecureUtils.getSpringSecurityUser().getId());
        return JsonResultImpl.getInstance(used);
    }

    /**
     * 修改用户密码
     * @param oldPasswd 旧密码
     * @param newPasswd 新密码
     * @param force 管理员使用无视旧密码强制修改
     */
    @PostMapping("{uid}/passwd")
    public JsonResult<?> modifyPassword(@RequestParam("old") String oldPasswd,
                                     @RequestParam("new") @Length(min = 6) String newPasswd,
                                     @PathVariable("uid") @UID long uid,
                                     @RequestParam(value = "force", defaultValue = "false") boolean force) throws AccessDeniedException {
        User user = SecureUtils.getSpringSecurityUser();
        if (force) {
            if (user == null || user.getType() != User.TYPE_ADMIN) {
                throw new AccessDeniedException("非管理员不允许使用force参数");
            } else {
                userDao.modifyPassword(uid, SecureUtils.getPassswd(newPasswd));
                tokenDao.cleanUserToken(uid);
                return JsonResultImpl.getInstance(200, null, "force reset");
            }
        } else {
            tokenDao.cleanUserToken(uid);
            int i = userService.modifyPasswd(uid, oldPasswd, newPasswd);
            return JsonResultImpl.getInstance(200, i, "ok");
        }
    }

    /**
     * 授予或撤销用户权限
     * @param uid  被操纵的用户ID
     * @param type 要设置的权限类型
     */
    @PutMapping("{uid}/type/{typeCode}")
    @RolesAllowed({"ADMIN"})
    public JsonResult<?> grant(@PathVariable("uid") long uid,
                            @PathVariable("typeCode") int type) {
        User user = userService.getUserById(uid);
        if (user == null) {
            throw new UserNoExistException();
        }
        userService.grant(uid, type);
        return JsonResult.emptySuccess();
    }

    /**
     * 取用户列表
     * @param page 页数
     * @param size 每页大小
     */
    @GetMapping("list")
    @RolesAllowed({"ADMIN"})
    public JsonResult<CommonPageInfo<User>> getUserList(@RequestParam(value = "page", defaultValue = "1") int page,
                                  @RequestParam(value = "size", defaultValue = "10") @Max(500) @Min(5) @Valid int size) {
        CommonPageInfo<User> res = userService.listUsers(new PageableRequest().setPage(page).setSize(size));
        res.getContent().forEach(e -> e.setPwd(null));
        return JsonResultImpl.getInstance(res);
    }

}
