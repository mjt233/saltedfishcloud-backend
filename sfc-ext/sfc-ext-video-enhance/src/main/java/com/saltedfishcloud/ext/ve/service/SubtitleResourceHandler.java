package com.saltedfishcloud.ext.ve.service;

import com.saltedfishcloud.ext.ve.constant.VEConstants;
import com.xiaotao.saltedfishcloud.constant.ResourceProtocol;
import com.xiaotao.saltedfishcloud.model.PermissionInfo;
import com.xiaotao.saltedfishcloud.model.dto.ResourceRequest;
import com.xiaotao.saltedfishcloud.service.resource.AbstractResourceProtocolHandler;
import com.xiaotao.saltedfishcloud.service.resource.ResourceService;
import com.xiaotao.saltedfishcloud.utils.ResourceUtils;
import com.xiaotao.saltedfishcloud.utils.TypeUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.Objects;

public class SubtitleResourceHandler extends AbstractResourceProtocolHandler<ResourceRequest> {

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private VideoService videoService;

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
    public Resource getFileResource(ResourceRequest resourceRequest, ResourceRequest param) throws IOException {
        Resource resource = videoService.getResource(param);
        String subtitleText;

        String stream = Objects.requireNonNull(TypeUtils.toString(param.getParams().get("stream")), "param.stream不能为空");
        String subtitleType = TypeUtils.toString(param.getParams().getOrDefault("type", VEConstants.SubtitleType.WEBVTT));
        subtitleText = videoService.getSubtitleText(resource, stream, subtitleType);
        return ResourceUtils.stringToResource(subtitleText)
                .setContentType("text/vtt");

    }

    @Override
    public String getProtocolName() {
        return "subtitle";
    }
}
