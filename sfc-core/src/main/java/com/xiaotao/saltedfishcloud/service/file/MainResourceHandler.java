package com.xiaotao.saltedfishcloud.service.file;

import com.sfc.constant.ResourceProtocol;
import com.sfc.constant.error.CommonError;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.model.dto.ResourceRequest;
import com.xiaotao.saltedfishcloud.model.po.User;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.resource.ResourceProtocolHandler;
import com.xiaotao.saltedfishcloud.service.resource.ResourceService;
import com.xiaotao.saltedfishcloud.service.user.UserService;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.xiaotao.saltedfishcloud.validator.UIDValidator;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Objects;

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
    @Autowired
    private UserService userService;

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

    @Override
    public boolean isWriteable() {
        return true;
    }

    /**
     * 校验文件写入参数
     */
    private void validWriteParam(ResourceRequest param) {
        long uid = Integer.parseInt(param.getTargetId());
        User user = SecureUtils.getSpringSecurityUser();
        if (user == null) {
            String createUid = Objects.requireNonNull(param.getParams().get(ResourceRequest.CREATE_UID), "缺失权限上下文会话或创建人id");
            user = Objects.requireNonNull(userService.getUserById(Long.valueOf(createUid)), "无效的创建人id");
        }
        if(!UIDValidator.validate(user, uid, true)) {
            throw new IllegalArgumentException("访问拒绝");
        }
    }

    @Override
    public void writeResource(ResourceRequest param, Resource resource) throws IOException {
        if (Boolean.TRUE.equals(param.getIsThumbnail())) {
            throw new IllegalArgumentException("不支持写入缩略图");
        }
        validWriteParam(param);
        Date now = new Date();
        long uid = Integer.parseInt(param.getTargetId());

        FileInfo fileInfo = new FileInfo();
        fileInfo.setCtime(now.getTime());
        fileInfo.setStreamSource(resource);
        
        fileInfo.setUid(uid);
        fileInfo.setName(param.getName());
        fileInfo.setSize(resource.contentLength());
        String md5 = param.getParams().get("md5");
        if (md5 != null) {
            fileInfo.setMd5(md5);
        }
        try(InputStream in = resource.getInputStream()) {
            fileSystemManager.getMainFileSystem().saveFile(uid, in, param.getPath(), fileInfo);
        }

    }
}
