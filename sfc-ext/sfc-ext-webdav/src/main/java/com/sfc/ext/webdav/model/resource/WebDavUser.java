package com.sfc.ext.webdav.model.resource;

import com.sfc.ext.webdav.model.po.WebDavAuth;
import com.xiaotao.saltedfishcloud.model.po.User;
import io.milton.http.Auth;
import io.milton.http.Request;
import io.milton.http.exceptions.BadRequestException;
import io.milton.http.exceptions.NotAuthorizedException;
import io.milton.principal.DiscretePrincipal;

import java.util.Date;

public class WebDavUser implements DiscretePrincipal {

    private final User user;
    private final WebDavAuth webDavAuth;

    public WebDavUser(User user, WebDavAuth webDavAuth) {
        this.user = user;
        this.webDavAuth = webDavAuth;
    }

    @Override
    public String getPrincipalURL() {
        return "";
    }

    @Override
    public PrincipleId getIdenitifer() {
        return null;
    }

    @Override
    public String getUniqueId() {
        return "";
    }

    @Override
    public String getName() {
        return "";
    }

    @Override
    public Object authenticate(String user, String password) {
        return null;
    }

    @Override
    public boolean authorise(Request request, Request.Method method, Auth auth) {
        return false;
    }

    @Override
    public String getRealm() {
        return "";
    }

    @Override
    public Date getModifiedDate() {
        return null;
    }

    @Override
    public String checkRedirect(Request request) throws NotAuthorizedException, BadRequestException {
        return "";
    }
}
