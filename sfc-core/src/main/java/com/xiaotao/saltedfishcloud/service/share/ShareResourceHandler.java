package com.xiaotao.saltedfishcloud.service.share;

import com.xiaotao.saltedfishcloud.constant.ResourceProtocol;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.model.PermissionInfo;
import com.xiaotao.saltedfishcloud.model.dto.ResourceRequest;
import com.xiaotao.saltedfishcloud.model.po.ShareInfo;
import com.xiaotao.saltedfishcloud.service.node.NodeService;
import com.xiaotao.saltedfishcloud.service.resource.AbstractResourceProtocolHandler;
import com.xiaotao.saltedfishcloud.service.share.entity.ShareExtractorDTO;
import com.xiaotao.saltedfishcloud.service.share.entity.ShareType;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import com.xiaotao.saltedfishcloud.utils.TypeUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

/**
 * 为统一资源访问接口添加文件分享的资源支持
 */
@Component
public class ShareResourceHandler extends AbstractResourceProtocolHandler<ResourceRequest> {
    @Autowired
    private ShareService shareService;
    @Autowired
    private NodeService nodeService;

    @Override
    public ResourceRequest validAndParseParam(ResourceRequest resourceRequest, boolean isWrite) {
        Object vid = resourceRequest.getParams().get("vid");
        if (vid == null) {
            throw new JsonException("缺少参数：vid");
        }
        return resourceRequest;
    }

    @Override
    public PermissionInfo getPermissionInfo(ResourceRequest resourceRequest, ResourceRequest param) {
        Object vid = param.getParams().get("vid");
        ShareInfo share = shareService.getShare(Long.parseLong(param.getTargetId()), vid.toString());
        return PermissionInfo.builder()
                .isWritable(false)
                .isReadable(true)
                .ownerUid(Optional.ofNullable(share).map(ShareInfo::getUid).orElse(null))
                .build();
    }

    @Override
    public String getPathMappingIdentity(ResourceRequest resourceRequest, ResourceRequest param) {
        Object vid = param.getParams().get("vid");
        ShareInfo share = shareService.getShare(Long.parseLong(param.getTargetId()), vid.toString());

        String basePath = nodeService.getPathByNode(share.getUid(), share.getParentId());
        if (share.getType() == ShareType.FILE) {
            return SecureUtils.getMd5(
                    share.getUid() + ":" + StringUtils.appendPath(basePath, param.getName())
            );
        }
        return SecureUtils.getMd5(
                share.getUid() + ":" + StringUtils.appendPath(
                        basePath,
                        share.getName(),
                        param.getPath(),
                        param.getName()
                )
        );
    }

    @Override
    public Resource getFileResource(ResourceRequest resourceRequest, ResourceRequest param) throws IOException {
        Object vid = param.getParams().get("vid");
        String code = TypeUtils.toString(param.getParams().getOrDefault("code", ""));
        return shareService.getFileResource(ShareExtractorDTO.builder()
                .code(code)
                .isThumbnail(param.getIsThumbnail())
                .name(param.getName())
                .path(param.getPath())
                .verification(vid.toString())
                .sid(Long.valueOf(param.getTargetId()))
                .build()
        );
    }
    @Override
    public String getProtocolName() {
        return ResourceProtocol.SHARE;
    }
}
