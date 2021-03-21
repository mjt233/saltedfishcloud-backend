package com.xiaotao.saltedfishcloud.service.file.path;

import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.po.file.BasicFileInfo;
import com.xiaotao.saltedfishcloud.helper.PathBuilder;
import org.springframework.stereotype.Component;

/**
 * 原始路径操作器，用户请求什么路径，就返回什么路径
 */
@Component("RawPathHandler")
public class RawPathHandler implements PathHandler{
    @Override
    public String getStorePath(int uid, String targetDir, BasicFileInfo fileInfo) {
        PathBuilder pathBuilder = new PathBuilder();
        pathBuilder.append(DiskConfig.getRawFileStoreRootPath(uid))
                .append(targetDir)
                .append(fileInfo.getName());
        return pathBuilder.toString();
    }
}
