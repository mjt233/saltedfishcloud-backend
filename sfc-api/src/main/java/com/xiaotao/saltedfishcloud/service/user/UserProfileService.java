package com.xiaotao.saltedfishcloud.service.user;

import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;

/**
 * 用户档案配置服务，用于管理用户头像，背景图等数据
 * todo 编写实现类
 */
public interface UserProfileService {


    /**
     * 获取用户头像资源
     * @param uid   用户ID
     * @return      用户未设置头像时，为null
     */
    Resource getAvatar(int uid);

    /**
     * 保存用户头像
     * @param uid   用户ID
     * @param inputStream 头像资源输入流
     */
    void saveAvatar(int uid, InputStream inputStream) throws IOException;
}
