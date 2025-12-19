package com.saltedfishcloud.ext.ve.service;

import com.xiaotao.saltedfishcloud.constant.ResourceProtocol;
import com.xiaotao.saltedfishcloud.model.PermissionInfo;
import com.xiaotao.saltedfishcloud.model.dto.ResourceRequest;
import com.xiaotao.saltedfishcloud.service.resource.AbstractResourceProtocolHandler;
import com.xiaotao.saltedfishcloud.service.resource.ResourceService;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import com.xiaotao.saltedfishcloud.utils.ResourceUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;

import java.io.IOException;

public class VideoInfoResourceHandler extends AbstractResourceProtocolHandler<ResourceRequest> {

    @Autowired
    private VideoService videoService;

    @Autowired
    private ResourceService resourceService;

    @Override
    public ResourceRequest validAndParseParam(ResourceRequest resourceRequest, boolean isWrite) {
        return resourceRequest;
    }

    @Override
    public String getPathMappingIdentity(ResourceRequest resourceRequest, ResourceRequest param) {
        ResourceRequest sourceResourceRequest = videoService.getSourceResourceRequest(param);
        if (sourceResourceRequest != param) {
            return resourceService.getResourceHandler(sourceResourceRequest.getProtocol()).getPathMappingIdentity(sourceResourceRequest) + "#" + getProtocolName();
        } else {
            return resourceService.getResourceHandler(ResourceProtocol.MAIN).getPathMappingIdentity(param) + "#" + getProtocolName();
        }
    }

    @Override
    public Resource getFileResource(ResourceRequest resourceRequest, ResourceRequest param) throws IOException {
        Resource resource = videoService.getResource(param);
        return ResourceUtils.stringToResource(MapperHolder.toJson(videoService.getVideoInfo(resource)))
                .setContentType("application/json;charset=utf-8");
    }


    @Override
    public PermissionInfo getPermissionInfo(ResourceRequest resourceRequest, ResourceRequest param) {
        PermissionInfo permissionInfo = resourceService.getResourceHandler(param.getProtocol())
                .getPermissionInfo(videoService.getSourceResourceRequest(param));
        return PermissionInfo.builder()
                .isReadable(permissionInfo.isReadable())
                .isWritable(false)
                .ownerUid(permissionInfo.getOwnerUid())
                .build();
    }

    @Override
    public String getProtocolName() {
        return "videoInfo";
    }
}
