package com.sfc.ext.webdav.service;

import com.sfc.ext.webdav.dao.WebDavAuthRepo;
import com.sfc.ext.webdav.enums.Constants;
import com.sfc.ext.webdav.model.po.WebDavAuth;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.model.po.UserPrincipal;
import com.xiaotao.saltedfishcloud.service.user.UserService;
import com.xiaotao.saltedfishcloud.utils.LockUtils;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import io.milton.http.http11.auth.DigestGenerator;
import io.milton.http.http11.auth.DigestResponse;
import io.milton.servlet.MiltonServlet;
import lombok.RequiredArgsConstructor;

import java.util.Objects;
import java.util.Optional;

@RequiredArgsConstructor
public class WebDavAuthService {
    private final WebDavAuthRepo webDavAuthRepo;
    private final DigestGenerator digestGenerator;
    private final UserService userService;

    /**
     * 检查是否已存在 WebDAV 认证配置
     */
    public boolean existAuth(Long uid) {
        return webDavAuthRepo.findOneByUid(uid) != null;
    }


    public UserPrincipal authenticate(String username, String password) {
        UserPrincipal user = Optional.ofNullable(userService.getUserByUser(username))
                .map(UserPrincipal::from)
                .filter(u -> Objects.equals(u.getPassword(), SecureUtils.getPassswd(password)))
                .orElse(null);
        Optional.ofNullable(MiltonServlet.request())
                .ifPresent(req -> {
                    req.setAttribute("userObj", user);
                    req.getSession(true).setAttribute("userObj", user);
                });
        return user;
    }

    public UserPrincipal authenticate(DigestResponse digestRequest) {
        UserPrincipal user = Optional.ofNullable(webDavAuthRepo.findOneByUsername(digestRequest.getUser()))
                .filter(auth -> Objects.equals(digestRequest.getResponseDigest(), digestGenerator.generateDigestWithEncryptedPassword(digestRequest, auth.getA1Md5())))
                .map(auth -> userService.getUserById(auth.getUid()))
                .map(UserPrincipal::from)
                .orElse(null);
        Optional.ofNullable(MiltonServlet.request())
                .ifPresent(req -> {
                    req.setAttribute("userObj", user);
                    req.getSession(true).setAttribute("userObj", user);
                });
        return user;
    }

    public void setWebDavPassword(Long uid, String originPassword) {
        LockUtils.execute("webdav::setPassword::" + uid, () -> {
            WebDavAuth auth = webDavAuthRepo.findOneByUid(uid);
            String username;
            if (auth == null) {
                UserPrincipal user = UserPrincipal.from(userService.getUserById(uid));
                if (user == null) {
                    throw new JsonException("无效的用户id");
                }
                auth = new WebDavAuth();
                auth.setUid(uid);
                auth.setUsername(user.getUsername());
                username = user.getUsername();
            } else {
                username = auth.getUsername();
            }
            auth.setA1Md5(digestGenerator.encodePasswordInA1Format(username, Constants.REALM, originPassword));
            webDavAuthRepo.save(auth);
        });
    }
}
