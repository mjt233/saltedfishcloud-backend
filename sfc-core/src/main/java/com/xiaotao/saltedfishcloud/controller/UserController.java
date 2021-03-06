package com.xiaotao.saltedfishcloud.controller;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.xiaotao.saltedfishcloud.config.SysRuntimeConfig;
import com.xiaotao.saltedfishcloud.annotations.AllowAnonymous;
import com.xiaotao.saltedfishcloud.constant.error.AccountError;
import com.xiaotao.saltedfishcloud.dao.mybatis.UserDao;
import com.xiaotao.saltedfishcloud.dao.redis.TokenDaoImpl;
import com.xiaotao.saltedfishcloud.entity.json.JsonResult;
import com.xiaotao.saltedfishcloud.entity.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.entity.po.QuotaInfo;
import com.xiaotao.saltedfishcloud.entity.po.User;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.exception.UserNoExistException;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemProvider;
import com.xiaotao.saltedfishcloud.service.user.UserService;
import com.xiaotao.saltedfishcloud.utils.JwtUtils;
import com.xiaotao.saltedfishcloud.utils.MultipartFileResource;
import com.xiaotao.saltedfishcloud.utils.ResourceUtils;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.xiaotao.saltedfishcloud.validator.annotations.UID;
import lombok.RequiredArgsConstructor;
import lombok.var;
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

@Controller
@RequestMapping(UserController.PREFIX)
@ResponseBody
@Validated
@RequiredArgsConstructor
public class UserController {
    public static final String PREFIX = "/api/user";

    private final UserService userService;
    private final DiskFileSystemProvider fileSystemFactory;
    private final UserDao userDao;
    private final TokenDaoImpl tokenDao;
    private final SysRuntimeConfig runtimeConfig;

    /**
     * ?????????token
     * @param kick ????????????token?????????????????????
     */
    @PostMapping("/updateToken")
    public JsonResult updateToken(@RequestParam(value = "kick", defaultValue = "true") boolean kick) {
        final User user = userService.getUserById(SecureUtils.getSpringSecurityUser().getId());
        if (kick) {
            tokenDao.cleanUserToken(user.getId());
        }
        String token = tokenDao.generateUserToken(user.getId());
        return JsonResultImpl.getInstance(token);
    }

    /**
     * ??????????????????
     * @param account   ??????????????????????????????
     * @param code      ???????????????
     * @param password  ?????????
     */
    @PostMapping("/resetPassword")
    @AllowAnonymous
    public JsonResult resetPassword(@RequestParam("account") String account,
                                    @RequestParam("code") String code,
                                    @RequestParam("password") @Length(min = 6) String password) {
        userService.resetPassword(account, code, password);
        return JsonResult.emptySuccess();
    }

    /**
     * ?????????????????????
     * @param email ?????????
     * @param originCode ???????????????????????????
     * @param newCode  ??????????????????
     */
    @PostMapping("/newMail")
    public JsonResult setEmail(@RequestParam("email") @Email String email,
                               @RequestParam(value = "originCode", required = false) String originCode,
                               @RequestParam("newCode") String newCode) {
        Integer uid = SecureUtils.getSpringSecurityUser().getId();
        userService.bindEmail(uid, email, originCode, newCode);
        return JsonResult.emptySuccess();
    }

    /**
     * ??????????????????????????????
     * @param email ?????????
     */
    @PostMapping("/sendBindEmail")
    public JsonResult sendBindEmail(@RequestParam("email") @Email String email) throws MessagingException, UnsupportedEncodingException {
        Integer uid = SecureUtils.getSpringSecurityUser().getId();
        userService.sendBindEmail(uid, email);
        return JsonResult.emptySuccess();
    }

    /**
     * ?????????????????????????????????????????????
     */
    @PostMapping("/sendVerifyEmail")
    public JsonResult sendVerifyEmail() throws MessagingException, UnsupportedEncodingException {
        userService.sendVerifyEmail(SecureUtils.getSpringSecurityUser().getId());
        return JsonResult.emptySuccess();
    }

    /**
     * ???????????????
     * @param code ?????????
     */
    @PostMapping("/verifyEmail")
    public JsonResult verifyEmail(@RequestParam("code") String code) throws MessagingException, UnsupportedEncodingException {
        userService.verifyEmail(SecureUtils.getSpringSecurityUser().getId(), code);
        return JsonResult.emptySuccess();
    }

    /**
     * ??????????????????????????????
     */
    @PostMapping("/sendResetPasswordEmail")
    @AllowAnonymous
    public JsonResult sendResetPasswordEmail(@RequestParam(value = "account") String account) throws MessagingException, UnsupportedEncodingException {
        userService.sendResetPasswordEmail(account);
        return JsonResult.emptySuccess();
    }

    /**
     * ???????????????????????????
     */
    @GetMapping("/regType")
    @AllowAnonymous
    public JsonResult getRegType() {
        return JsonResultImpl.getInstance(new HashMap<String, Boolean>(){{
            put("email", runtimeConfig.isEnableEmailReg());
            put("regcode", runtimeConfig.isEnableRegCode());
        }});
    }

    /**
     * ????????????????????????????????????token?????????
     */
    @GetMapping
    public JsonResult getUserInfo(HttpServletRequest request) throws UserNoExistException {
        var user =  SecureUtils.getSpringSecurityUser();
        if (user == null) {
            throw new JsonException(401, "?????????");
        }
        tokenDao.setToken(user.getId(), request.getHeader(JwtUtils.AUTHORIZATION));
        return JsonResultImpl.getInstance(user);
    }


    /**
     * ?????????????????????
     * @param email ??????
     */
    @PostMapping("/regcode")
    @AllowAnonymous
    public JsonResult sendRegCode(@Validated @NotBlank @RequestParam("email") @Email String email) {
        userService.sendRegEmail(email);
        return JsonResult.emptySuccess();
    }

    /**
     * ?????????????????????????????????????????????????????????
     * @param user  ?????????
     * @param rawPassword   ?????????????????????????????????
     * @param regCode   ???????????????
     */
    @PostMapping
    @AllowAnonymous
    public JsonResult regUser(@RequestParam("user") String user,
                              @RequestParam("passwd") @Length(min = 6) String rawPassword,
                              @RequestParam(value = "regcode", defaultValue = "") String regCode,
                              @RequestParam("email") @Email String email,
                              @RequestParam(value = "type", defaultValue = "0") int type,
                              @RequestParam(value = "validEmail", defaultValue = "false") Boolean validEmail
                              ) throws JsonException {



        // ??????????????????????????????????????????
        if (SecureUtils.getSpringSecurityUser() != null && SecureUtils.getSpringSecurityUser().getType() == User.TYPE_ADMIN) {
            userService.addUser(user, rawPassword, email, type == User.TYPE_ADMIN ? User.TYPE_ADMIN : User.TYPE_COMMON);
        } else {
            userService.addUser(user, rawPassword, email, regCode, validEmail);
        }
        return JsonResult.emptySuccess();
    }

    /**
     * ??????????????????
     * @param file  ????????????
     */
    @PostMapping("avatar")
    public JsonResult uploadAvatar(@RequestParam("file") MultipartFile file) throws IOException {
        fileSystemFactory.getFileSystem().saveAvatar(
                SecureUtils.getSpringSecurityUser().getId(),
                new MultipartFileResource(file)
        );
        return JsonResult.emptySuccess();
    }

    /**
     * ???????????????????????????????????????????????????
     * @param username  ?????????
     */
    @GetMapping({
            "avatar/{username}",
            "avatar"
    })
    @AllowAnonymous
    public ResponseEntity<Resource>
                getAvatar(HttpServletResponse response, @PathVariable(required = false) String username) throws IOException {
        User currentUser = SecureUtils.getSpringSecurityUser();
        if (currentUser == null && username == null) {
            response.sendRedirect("/api/static/defaultAvatar.png");
            return null;
        }
        if (username == null) {
            return ResourceUtils.wrapResource(fileSystemFactory.getFileSystem().getAvatar(currentUser.getId()));
        } else {
            User user = userService.getUserByUser(username);
            if (user == null) {
                throw new JsonException(AccountError.USER_NOT_EXIST);
            } else {
                return ResourceUtils.wrapResource(fileSystemFactory.getFileSystem().getAvatar(user.getId()));
            }
        }
    }

    /**
     * ????????????????????????????????????
     */
    @GetMapping("quota")
    public JsonResult getQuotaUsed() {
        QuotaInfo used = userDao.getUserQuotaUsed(SecureUtils.getSpringSecurityUser().getId());
        return JsonResultImpl.getInstance(used);
    }

    /**
     * ??????????????????
     * @param oldPasswd ?????????
     * @param newPasswd ?????????
     * @param force ??????????????????????????????????????????
     */
    @PostMapping("{uid}/passwd")
    public JsonResult modifyPassword(@RequestParam("old") String oldPasswd,
                                     @RequestParam("new") @Length(min = 6) String newPasswd,
                                     @PathVariable("uid") @UID int uid,
                                     @RequestParam(value = "force", defaultValue = "false") boolean force) throws AccessDeniedException {
        User user = SecureUtils.getSpringSecurityUser();
        if (force) {
            if ( user.getType() != User.TYPE_ADMIN) {
                throw new AccessDeniedException("???????????????????????????force??????");
            } else {
                userDao.modifyPassword(uid, SecureUtils.getPassswd(newPasswd));
                tokenDao.cleanUserToken(user.getId());
                return JsonResultImpl.getInstance(200, null, "force reset");
            }
        } else {
            tokenDao.cleanUserToken(user.getId());
            int i = userService.modifyPasswd(uid, oldPasswd, newPasswd);
            return JsonResultImpl.getInstance(200, i, "ok");
        }
    }

    /**
     * ???????????????????????????
     * @param uid  ??????????????????ID
     * @param type ????????????????????????
     */
    @PutMapping("{uid}/type/{typeCode}")
    @RolesAllowed({"ADMIN"})
    public JsonResult grant(@PathVariable("uid") int uid,
                            @PathVariable("typeCode") int type) {
        User user = userDao.getUserById(uid);
        if (user == null) {
            throw new UserNoExistException();
        }
        userService.grant(uid, type);
        return JsonResult.emptySuccess();
    }

    /**
     * ???????????????
     * @param page ??????
     * @param size ????????????
     */
    @GetMapping("list")
    @RolesAllowed({"ADMIN"})
    public JsonResult getUserList(@RequestParam(value = "page", defaultValue = "1") int page,
                                  @RequestParam(value = "size", defaultValue = "10") @Max(50) @Min(5) @Valid int size) {
        PageHelper.startPage(page, 10);
        List<User> userList = userDao.getUserList();
        userList.forEach(e -> e.setPwd(null));
        PageInfo<User> pageInfo = new PageInfo<>(userList);
        return JsonResultImpl.getInstance(pageInfo);
    }

}
