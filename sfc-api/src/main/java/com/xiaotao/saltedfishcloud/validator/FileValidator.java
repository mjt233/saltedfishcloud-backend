package com.xiaotao.saltedfishcloud.validator;

import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class FileValidator {
    // 可接收的头像文件后缀名
    public static final List<String> ACCEPT_AVATAR_TYPE = Arrays.asList("jpg", "jpeg", "gif", "png");

    public static boolean validateAvatar(Resource resource) {
        //  文件属性约束：大小不得大于3MiB，限定文件类型
        try {
            if (resource.contentLength() > 1024*1024*3) {
                throw new JsonException(400, "文件过大");
            }
            String name = resource.getFilename();
            if (name == null) {
                throw new JsonException(400, "不支持的格式，只支持jpg, jpeg, gif, png");
            }

            String suffix = FileUtils.getSuffix(name);
            if ( !ACCEPT_AVATAR_TYPE.contains(suffix)) {
                throw new JsonException(400, "不支持的格式，只支持jpg, jpeg, gif, png");
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}
