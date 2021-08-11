package com.xiaotao.saltedfishcloud.service.breakpoint.manager.impl.utils;

import com.xiaotao.saltedfishcloud.utils.PathUtils;

import java.nio.file.Path;
import java.nio.file.Paths;

public class TaskStorePath {
    /**
     * 获取任务数据文件夹路径
     * @param id 任务ID
     */
    public static Path getRoot(String id) {
        if (id == null) {
            throw new NullPointerException();
        }
        return Paths.get(PathUtils.getTempDirectory() + "/xyy/" + id);
    }

    /**
     * 获取文件块路径
     * @param id    任务ID
     * @param part  文件块
     */
    public static Path getPartFile(String id, int part) {
        return Paths.get(getRoot(id) + "/" + part + ".part");
    }

    /**
     * 获取任务元数据路径
     * @param id    任务ID
     */
    public static Path getMetadata(String id) {
        return Paths.get(getRoot(id) + "/metadata.json");
    }
}
