package com.xiaotao.saltedfishcloud.controller;

import com.xiaotao.saltedfishcloud.annotations.AllowAnonymous;
import com.xiaotao.saltedfishcloud.constant.ByteSize;
import com.sfc.cloud.constant.SfcUserConstants;
import com.xiaotao.saltedfishcloud.dao.redis.TokenServiceImpl;
import com.sfc.cloud.exception.SfcJsonException;
import com.xiaotao.saltedfishcloud.exception.UserNoExistException;
import com.sfc.cloud.model.SfcPageInfo;
import com.xiaotao.saltedfishcloud.model.config.SysCommonConfig;
import com.sfc.cloud.model.json.SfcJsonResult;
import com.sfc.cloud.model.param.SfcPageableRequest;
import com.xiaotao.saltedfishcloud.model.po.QuotaInfo;
import com.xiaotao.saltedfishcloud.model.po.User;
import com.xiaotao.saltedfishcloud.model.po.UserPrincipal;
import com.xiaotao.saltedfishcloud.service.file.UserCustomStoreService;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.service.file.FileRecordService;
import com.xiaotao.saltedfishcloud.service.user.UserService;
import com.xiaotao.saltedfishcloud.utils.*;
import com.sfc.cloud.data.validation.SfcUID;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.security.RolesAllowed;
import jakarta.mail.MessagingException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.Length;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

@Slf4j
@Controller
@RequestMapping(UserController.PREFIX)
@ResponseBody
@Validated
@RequiredArgsConstructor
public class UserController {
    public static final String PREFIX = "/api/user";

    private final UserService userService;
    private final UserCustomStoreService userCustomStoreService;
    private final TokenServiceImpl tokenDao;
    private final SysCommonConfig sysCommonConfig;

    @Operation(summary = "验证用户重置密码时输入的验证码是否正确")
    @PostMapping("/validResetPasswordEmailCode")
    @AllowAnonymous
    public SfcJsonResult<Boolean> validResetPasswordEmailCode(@RequestParam("account") String account,
                                                  @RequestParam("code") String code) {
        return SfcJsonResult.getInstance(userService.validResetPasswordEmailCode(account, code));
    }

    @PostMapping("findBaseUserInfo")
    @AllowAnonymous
    public SfcJsonResult<List<User>> findBaseUserInfo(@RequestBody List<Long> ids) {
        return SfcJsonResult.getInstance(userService.findBaseInfoByIds(ids));
    }

    /**
     * 获取新token
     * @param kick 是否使旧token失效（踢下线）
     */
    @PostMapping("/updateToken")
    public SfcJsonResult<String> updateToken(@RequestParam(value = "kick", defaultValue = "true") boolean kick) {
        final User user = userService.getUserById(SecureUtils.getSpringSecurityUser().getId());
        if (kick) {
            tokenDao.cleanUserToken(user.getId());
        }
        String token = tokenDao.generateUserToken(user.getId());
        return SfcJsonResult.getInstance(token);
    }

    /**
     * 重置用户密码
     * @param account   用户用户名或用户邮箱
     * @param code      邮箱验证码
     * @param password  新密码
     */
    @PostMapping("/resetPassword")
    @AllowAnonymous
    public SfcJsonResult<?> resetPassword(@RequestParam("account") String account,
                                    @RequestParam("code") String code,
                                    @RequestParam("password") @Length(min = 6) String password) {
        userService.resetPassword(account, code, password);
        return SfcJsonResult.emptySuccess();
    }

    /**
     * 用户绑定新邮箱
     * @param email 新邮箱
     * @param originCode 旧邮箱验证码，可空
     * @param newCode  新邮箱验证码
     */
    @PostMapping("/newMail")
    public SfcJsonResult<?> setEmail(@RequestParam("email") @Email String email,
                               @RequestParam(value = "originCode", required = false) String originCode,
                               @RequestParam("newCode") String newCode) {
        Long uid = SecureUtils.getSpringSecurityUser().getId();
        userService.bindEmail(uid, email, originCode, newCode);
        return SfcJsonResult.emptySuccess();
    }

    /**
     * 发送新邮箱绑定验证码
     * @param email 新邮箱
     */
    @PostMapping("/sendBindEmail")
    public SfcJsonResult<?> sendBindEmail(@RequestParam("email") @Email String email) throws MessagingException, UnsupportedEncodingException {
        Long uid = SecureUtils.getSpringSecurityUser().getId();
        userService.sendBindEmail(uid, email);
        return SfcJsonResult.emptySuccess();
    }

    /**
     * 发送用于验证旧邮箱的邮箱验证码
     */
    @PostMapping("/sendVerifyEmail")
    public SfcJsonResult<?> sendVerifyEmail() throws MessagingException, UnsupportedEncodingException {
        userService.sendVerifyEmail(SecureUtils.getSpringSecurityUser().getId());
        return SfcJsonResult.emptySuccess();
    }

    /**
     * 验证旧邮箱
     * @param code 验证码
     */
    @PostMapping("/verifyEmail")
    public SfcJsonResult<?> verifyEmail(@RequestParam("code") String code) throws MessagingException, UnsupportedEncodingException {
        userService.verifyEmail(SecureUtils.getSpringSecurityUser().getId(), code);
        return SfcJsonResult.emptySuccess();
    }

    /**
     * 发送重置密码验证邮件
     */
    @PostMapping("/sendResetPasswordEmail")
    @AllowAnonymous
    public SfcJsonResult<?> sendResetPasswordEmail(@RequestParam(value = "account") String account) throws MessagingException, UnsupportedEncodingException {
        userService.sendResetPasswordEmail(account);
        return SfcJsonResult.emptySuccess();
    }

    /**
     * 获取允许的注册类型
     */
    @GetMapping("/regType")
    @AllowAnonymous
    public SfcJsonResult<?> getRegType() {
        return SfcJsonResult.getInstance(new HashMap<String, Boolean>(){{
            put("email", sysCommonConfig.getEnableEmailReg());
            put("regcode", sysCommonConfig.getEnableRegCode());
        }});
    }

    /**
     * 获取用户基本信息，并刷新token有效期
     */
    @GetMapping
    public SfcJsonResult<UserPrincipal> getUserInfo(HttpServletRequest request) throws UserNoExistException {
        UserPrincipal user = SecureUtils.getSpringSecurityUser();
        if (user == null) {
            throw new SfcJsonException(401, "未登录");
        }
        Optional.ofNullable(request.getHeader(JwtUtils.AUTHORIZATION))
                .or(() -> Arrays.stream(request.getCookies())
                        .filter(c -> "token".equals(c.getName()))
                        .map(Cookie::getValue)
                        .findAny())
                .ifPresent(token -> tokenDao.setToken(user.getId(), token));
        return SfcJsonResult.getInstance(user);
    }

    /**
     * 登出
     */
    @PostMapping("logout")
    @ResponseBody
    public SfcJsonResult<Object> logout(HttpServletRequest request, HttpServletResponse response) {
        // 获取原token
        String token = SecureUtils.getToken(request);

        // 清理cookie
        Cookie cookie = new Cookie("token", "");
        cookie.setMaxAge(0);
        cookie.setPath("/");
        response.addCookie(cookie);

        // 令token失效
        if (token != null) {
            tokenDao.invalidToken(SecureUtils.getCurrentUid(), token);
        }
        return SfcJsonResult.emptySuccess();
    }


    /**
     * 发送注册验证码
     * @param email 邮件
     */
    @PostMapping("/regcode")
    @AllowAnonymous
    public SfcJsonResult<?> sendRegCode(@Validated @NotBlank @RequestParam("email") @Email String email) {
        userService.sendRegEmail(email);
        return SfcJsonResult.emptySuccess();
    }

    /**
     * 注册新账号，管理员可直接添加无需邀请码
     * @param user  用户名
     * @param rawPassword   原始密码（即密码原文）
     * @param regCode   注册邀请码
     */
    @PostMapping
    @AllowAnonymous
    public SfcJsonResult<?> regUser(@RequestParam("user") String user,
                              @RequestParam("passwd") @Length(min = 6) String rawPassword,
                              @RequestParam(value = "regcode", defaultValue = "") String regCode,
                              @RequestParam("email") @Email String email,
                              @RequestParam(value = "type", defaultValue = "0") int type,
                              @RequestParam(value = "validEmail", defaultValue = "false") Boolean validEmail
                              ) throws SfcJsonException {



        // 管理员直接添加，不受任何约束
        UserPrincipal principal = SecureUtils.getSpringSecurityUser();
        if (principal != null && principal.isAdmin()) {
            userService.addUser(user, rawPassword, email, type == SfcUserConstants.TYPE_ADMIN ? SfcUserConstants.TYPE_ADMIN : SfcUserConstants.TYPE_COMMON);
        } else {
            userService.addUser(user, rawPassword, email, regCode, validEmail);
        }
        return SfcJsonResult.emptySuccess();
    }

    /**
     * 上传用户头像
     * @param file  头像文件
     */
    @PostMapping("avatar")
    public SfcJsonResult<?> uploadAvatar(@RequestParam("file") MultipartFile file) throws IOException {
        userCustomStoreService.saveAvatar(SecureUtils.getSpringSecurityUser().getId(), new MultipartFileResource(file));
        return SfcJsonResult.emptySuccess();
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
        UserPrincipal currentUser = SecureUtils.getSpringSecurityUser();
        Resource avatar;
        if (currentUser == null && username == null && uid == null) {
            avatar = userCustomStoreService.getDefaultAvatar();
            response.setHeader("is-default-avatar", "1");
            return ResourceUtils.wrapResource(avatar);
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
        avatar = userCustomStoreService.getAvatar(finalUid);
        if (avatar == null) {
            avatar = userCustomStoreService.getDefaultAvatar();
            response.setHeader("is-default-avatar", "1");
        }
        return ResourceUtils.wrapResource(avatar);
    }

    /**
     * 获取用户空间配额使用情况
     */
    @GetMapping("quota")
    public SfcJsonResult<QuotaInfo> getQuotaUsed() {
        Long uid = SecureUtils.getCurrentUid();
        User user = userService.getUserById(uid);
        QuotaInfo quotaInfo = new QuotaInfo();
        quotaInfo.setUsed(Optional.ofNullable(SpringContextUtils.getContext().getBean(FileRecordService.class).getUsage(user.getId())).orElse(0L));
        quotaInfo.setQuota(Optional.ofNullable(user.getQuota()).orElse(0L) * ByteSize._1GiB);
        return SfcJsonResult.getInstance(quotaInfo);
    }

    /**
     * 修改用户密码
     * @param oldPasswd 旧密码
     * @param newPasswd 新密码
     * @param force 管理员使用无视旧密码强制修改
     */
    @PostMapping("{uid}/passwd")
    public SfcJsonResult<?> modifyPassword(@RequestParam("old") String oldPasswd,
                                     @RequestParam("new") @Length(min = 6) String newPasswd,
                                     @PathVariable("uid") @SfcUID long uid,
                                     @RequestParam(value = "force", defaultValue = "false") boolean force) throws AccessDeniedException {
        UserPrincipal user = SecureUtils.getSpringSecurityUser();
        if (force) {
            if (user == null || !user.isAdmin()) {
                throw new AccessDeniedException("非管理员不允许使用force参数");
            } else {
                userService.resetPasswd(uid, newPasswd);
                return SfcJsonResult.getInstance(200, null, "force reset");
            }
        } else {
            int i = userService.modifyPasswd(uid, oldPasswd, newPasswd);
            return SfcJsonResult.getInstance(200, i, "ok");
        }
    }

    /**
     * 授予或撤销用户权限
     * @param uid  被操纵的用户ID
     * @param type 要设置的权限类型
     */
    @PutMapping("{uid}/type/{typeCode}")
    @RolesAllowed({"ADMIN"})
    public SfcJsonResult<?> grant(@PathVariable("uid") long uid,
                            @PathVariable("typeCode") int type) {
        User user = userService.getUserById(uid);
        if (user == null) {
            throw new UserNoExistException();
        }
        userService.grant(uid, type);
        return SfcJsonResult.emptySuccess();
    }

    /**
     * 取用户列表
     * @param page 页数
     * @param size 每页大小
     */
    @GetMapping("list")
    @RolesAllowed({"ADMIN"})
    public SfcJsonResult<SfcPageInfo<User>> getUserList(@RequestParam(value = "page", defaultValue = "1") int page,
                                  @RequestParam(value = "size", defaultValue = "10") @Max(500) @Min(5) @Valid int size) {
        SfcPageInfo<User> res = userService.listUsers(new SfcPageableRequest().setPage(page).setSize(size));
        res.getContent().forEach(e -> e.setPwd(null));
        return SfcJsonResult.getInstance(res);
    }

    /**
     * 按用户名或邮箱模糊搜索用户（分页），仅管理员可用
     * @param keyword   搜索关键词，模糊匹配用户名或邮箱
     * @param page      页数（从1开始）
     * @param size      每页大小
     */
    @GetMapping("search")
    @RolesAllowed({"ADMIN"})
    public SfcJsonResult<SfcPageInfo<User>> searchUsers(@RequestParam("keyword") String keyword,
                                                        @RequestParam(value = "page", defaultValue = "1") int page,
                                                        @RequestParam(value = "size", defaultValue = "10") @Max(500) @Min(5) @Valid int size) {
        SfcPageInfo<User> res = userService.searchUsers(keyword, new SfcPageableRequest().setPage(page).setSize(size));
        res.getContent().forEach(e -> e.setPwd(null));
        return SfcJsonResult.getInstance(res);
    }

}
