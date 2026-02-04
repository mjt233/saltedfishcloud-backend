package com.sfc.ext.webdav.core;

import com.sfc.ext.webdav.enums.Constants;
import com.sfc.ext.webdav.enums.ResourceArea;
import com.sfc.ext.webdav.model.property.WebDavProperty;
import com.sfc.ext.webdav.model.resource.WebDavItem;
import com.sfc.ext.webdav.model.resource.WebDavRoot;
import com.sfc.ext.webdav.service.WebDavAuthService;
import com.xiaotao.saltedfishcloud.model.po.User;
import com.xiaotao.saltedfishcloud.service.user.UserService;
import com.xiaotao.saltedfishcloud.utils.SpringContextUtils;
import com.xiaotao.saltedfishcloud.validator.UIDValidator;
import io.milton.http.Auth;
import io.milton.http.Request;
import io.milton.http.SecurityManager;
import io.milton.http.annotated.AnnoResource;
import io.milton.http.http11.auth.DigestResponse;
import io.milton.resource.Resource;
import io.milton.servlet.MiltonServlet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.util.Lazy;

import java.util.Optional;

@Slf4j
public class SfcSecurityManager implements SecurityManager {
    private final Lazy<UserService> userService = Lazy.of(() -> SpringContextUtils.getContext().getBean(UserService.class));
    private final Lazy<WebDavAuthService> webDavAuthService = Lazy.of(() -> SpringContextUtils.getContext().getBean(WebDavAuthService.class));
    private final Lazy<WebDavProperty> webDavProperty = Lazy.of(() -> SpringContextUtils.getContext().getBean(WebDavProperty.class));

    public Optional<User> getUser(String username) {
        return userService.getOptional()
                .map(us -> us.getUserByAccount(username));
    }

    @Override
    public Object authenticate(DigestResponse digestRequest) {
        return webDavAuthService.get().authenticate(digestRequest);
    }

    @Override
    public Object authenticate(String username, String password) {
        return webDavAuthService.get().authenticate(username, password);
    }

    /**
     * 判断请求是否来自 Windows 自带的资源管理器 WebDAV 功能
     */
    private boolean isFromWindow(Request request) {
        return Optional.ofNullable(request.getRequestHeader(Request.Header.USER_AGENT))
                .filter(ua -> ua.startsWith("Microsoft-WebDAV-MiniRedir/"))
                .isPresent();
    }

    /**
     * 判断是否对虚拟目录本身的请求
     */
    private boolean isVirtualRootPathRequest(Request request) {
        String requirePath = Optional.ofNullable(request.getDestinationHeader())
                .or(() -> Optional.ofNullable(request.getAbsoluteUrl()))
                .map(url -> url.replaceAll("/+$", ""))
                .orElse(null);
        if (requirePath == null || requirePath.equals("/") || requirePath.isEmpty()) {
            return true;
        }
        String host = request.getHostHeader();
        String scheme = MiltonServlet.request().getScheme();
        return requirePath.equals(scheme + "://" + host + "/" + ResourceArea.PUBLIC.getName()) ||
                requirePath.equals(scheme + "://" + host + "/" + ResourceArea.PRIVATE.getName());
    }

    @Override
    public boolean authorise(Request request, Request.Method method, Auth auth, Resource resource) {
        if (!(resource instanceof AnnoResource r)) {
            return false;
        }
        Object source = r.getSource();

        // 针对 Windows 处理，不允许匿名登录，强制要求输入用户名和密码以确保有权限访问private目录
        // 匿名登录情况下访问private触发401会导致整个Windows拒绝访问整个 WebDAV 服务，再也无法访问该WebDAV服务
        boolean isNotAllowAnonymous = !Boolean.TRUE.equals(webDavProperty.get().getIsAllowAnonymous()) || isFromWindow(request);
        User sessionUser = (User) MiltonServlet.request().getSession().getAttribute("userObj");
        User authUser = Optional.ofNullable(sessionUser)
                .or(() -> Optional.ofNullable(auth)
                        .map(Auth::getTag)
                        .filter(tag -> tag instanceof User)
                        .map(tag -> (User) tag))
                .orElse(null);

        if (isNotAllowAnonymous && authUser == null) {
            return false;
        }
        boolean isRead = !method.isWrite;
        boolean isWrite = !isRead;


        // 根资源和虚拟根目录只允许读
        if (source instanceof WebDavRoot) {
            return isRead;
        }
        if (!(source instanceof WebDavItem item)) {
            return false;
        }
        if (item.isVirtualRoot() && isWrite && Request.Method.MKCOL != method && isVirtualRootPathRequest(request)) {
            return false;
        }

        boolean isPublic = item.getResourceArea() == ResourceArea.PUBLIC;
        if (isPublic) {
            // 公共网盘只读允许任何人访问
            if (isRead) {
                return true;
            }
        }

        // 私人网盘资源 需要验证登录人是资源所属人
        // 公共网盘资源写操作 需要验证登录人是管理员
        if (authUser == null) {
            return false;
        }
        Long requireUid;
        if (item.getUid() == null) {
            if (r.getParent() == r.getRoot()) {
                requireUid = authUser.getId();
            } else {
                return false;
            }
        } else {
            requireUid = item.getUid();
        }
        return UIDValidator.validate(authUser, requireUid, true);
    }

    @Override
    public String getRealm(String s) {
        return Constants.REALM;
    }

    @Override
    public boolean isDigestAllowed() {
        return true;
    }
}
