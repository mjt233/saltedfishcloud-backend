package com.xiaotao.saltedfishcloud.service.file;

import com.xiaotao.saltedfishcloud.constant.ResourceProtocol;
import com.xiaotao.saltedfishcloud.constant.error.CommonError;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.model.dto.ResourceRequest;
import com.xiaotao.saltedfishcloud.service.resource.ResourceProtocolHandler;
import com.xiaotao.saltedfishcloud.service.resource.ResourceService;
import com.xiaotao.saltedfishcloud.validator.UIDValidator;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 主文件系统的资源协议操作器，提供从主文件系统中获取文件及其缩略图。
 * 由于默认的文件系统管理器提供的主文件系统，是对原始文件系统的代理对象，内部实现了对挂载文件系统的路径重定向与对应的方法调用，因此该操作器同样也访问已挂载到主文件系统路径中的其他挂载文件系统。
 *
 */
@Component
public class MainResourceHandler implements ResourceProtocolHandler, InitializingBean {
    @Autowired
    private ResourceService resourceService;
    @Autowired
    private DiskFileSystemManager fileSystemManager;

    @Override
    public void afterPropertiesSet() throws Exception {
        resourceService.addResourceHandler(this);
    }

    @Override
    public Resource getFileResource(ResourceRequest param) throws IOException {
        if(!UIDValidator.validate(Long.parseLong(param.getTargetId()), false)) {
            throw new JsonException(CommonError.SYSTEM_FORBIDDEN);
        }
        if (param.getIsThumbnail()) {
            return fileSystemManager.getMainFileSystem().getThumbnail(Integer.parseInt(param.getTargetId()), param.getPath(), param.getName());
        } else {
            return fileSystemManager.getMainFileSystem().getResource(Integer.parseInt(param.getTargetId()), param.getPath(), param.getName());
        }

    }

    @Override
    public String getProtocolName() {
        return ResourceProtocol.MAIN;
    }
}
