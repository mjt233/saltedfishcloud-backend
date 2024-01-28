package com.saltedfishcloud.ext.ve.utils;

import lombok.experimental.UtilityClass;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;

import java.io.IOException;

@UtilityClass
public class VideoResourceUtils {
    public static String toLocalPath(Resource resource) {
        if (resource == null) {
            throw new IllegalArgumentException("资源为null");
        }
        if (resource instanceof PathResource) {
            return ((PathResource) resource).getPath();
        }
        try {
            return resource.getFile().getAbsolutePath();
        } catch (IOException e) {
            throw new IllegalArgumentException("该资源无法被解析为本地文件系统中的文件", e);
        }
    }
}
