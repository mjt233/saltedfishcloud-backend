package com.xiaotao.saltedfishcloud.service.file;

import com.xiaotao.saltedfishcloud.constant.ResourceProtocol;
import com.xiaotao.saltedfishcloud.constant.error.CommonError;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.helper.OutputStreamConsumer;
import com.xiaotao.saltedfishcloud.model.PermissionInfo;
import com.xiaotao.saltedfishcloud.model.dto.ResourceRequest;
import com.xiaotao.saltedfishcloud.model.po.User;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.resource.AbstractWritableResourceProtocolHandler;
import com.xiaotao.saltedfishcloud.service.user.UserService;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import com.xiaotao.saltedfishcloud.utils.TypeUtils;
import com.xiaotao.saltedfishcloud.validator.UIDValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * 主文件系统的资源协议操作器，提供从主文件系统中获取文件及其缩略图。
 * 由于默认的文件系统管理器提供的主文件系统，是对原始文件系统的代理对象，内部实现了对挂载文件系统的路径重定向与对应的方法调用，因此该操作器同样也访问已挂载到主文件系统路径中的其他挂载文件系统。
 *
 */
@Component
public class MainResourceHandler extends AbstractWritableResourceProtocolHandler<ResourceRequest> {

    @Autowired
    private DiskFileSystemManager fileSystemManager;

    @Autowired
    private UserService userService;

    @Override
    public PermissionInfo getPermissionInfo(ResourceRequest resourceRequest, ResourceRequest param) {
        long targetId = Long.parseLong(resourceRequest.getTargetId());
        return PermissionInfo.builder()
                .ownerUid(targetId)
                .isReadable(UIDValidator.validate(targetId, false))
                .isWritable(UIDValidator.validate(targetId, true))
                .build();
    }

    @Override
    public Resource getFileResource(ResourceRequest resourceRequest, ResourceRequest param) throws IOException {
        if(!UIDValidator.validate(Long.parseLong(resourceRequest.getTargetId()), false)) {
            throw new JsonException(CommonError.SYSTEM_FORBIDDEN);
        }
        if (resourceRequest.getIsThumbnail()) {
            return fileSystemManager.getMainFileSystem().getThumbnail(Long.parseLong(resourceRequest.getTargetId()), resourceRequest.getPath(), resourceRequest.getName());
        } else {
            return fileSystemManager.getMainFileSystem().getResource(Long.parseLong(resourceRequest.getTargetId()), resourceRequest.getPath(), resourceRequest.getName());
        }
    }

    @Override
    public String getPathMappingIdentity(ResourceRequest resourceRequest, ResourceRequest param) {
        return SecureUtils.getMd5(resourceRequest.getTargetId() + ":" + StringUtils.appendPath(resourceRequest.getPath(), resourceRequest.getName()));
    }

    @Override
    public String getProtocolName() {
        return ResourceProtocol.MAIN;
    }

    @Override
    public boolean isWriteable() {
        return true;
    }

    @Override
    public ResourceRequest validAndParseParam(ResourceRequest resourceRequest) {
        long uid = Long.parseLong(resourceRequest.getTargetId());
        User user = SecureUtils.getSpringSecurityUser();
        if (user == null) {
            Long createUid = Objects.requireNonNull(TypeUtils.toLong(resourceRequest.getParams().get(ResourceRequest.CREATE_UID)), "缺失权限上下文会话或创建人id");
            user = Objects.requireNonNull(userService.getUserById(createUid), "无效的创建人id");
        }
        if(!UIDValidator.validate(user, uid, true)) {
            throw new IllegalArgumentException("访问拒绝");
        }
        return resourceRequest;
    }

    @Override
    public void handleWriteResource(ResourceRequest resourceRequest, ResourceRequest parsedParam, OutputStreamConsumer<OutputStream> streamConsumer) throws IOException {
        FileInfo fileInfo = this.createFileInfoFromRequest(resourceRequest, null);
        fileSystemManager.getMainFileSystem().saveFileByStream(fileInfo, resourceRequest.getPath(), streamConsumer);
    }
}
