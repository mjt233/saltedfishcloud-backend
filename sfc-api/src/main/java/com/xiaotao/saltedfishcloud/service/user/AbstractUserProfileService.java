package com.xiaotao.saltedfishcloud.service.user;

import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;

public abstract class AbstractUserProfileService implements UserProfileService {
    @Override
    public Resource getAvatar(int uid) {
        return null;
    }

    @Override
    public void saveAvatar(int uid, InputStream inputStream) throws IOException {

    }
}
