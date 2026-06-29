package com.sfc.dm.service.resource;

import com.sfc.dm.service.InvalidDataService;
import com.xiaotao.saltedfishcloud.model.PermissionInfo;
import com.xiaotao.saltedfishcloud.model.dto.ResourceRequest;
import com.xiaotao.saltedfishcloud.service.resource.AbstractResourceProtocolHandler;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;

import java.io.IOException;

/**
 * 失效数据资源协议操作器。
 * <p>通过失效数据记录ID获取对应的物理存储文件内容，忽略path参数。
 * 仅支持读取操作，无需鉴权。</p>
 */
public class InvalidDataResourceHandler extends AbstractResourceProtocolHandler<ResourceRequest> {

    public static final String PROTOCOL_NAME = "invalid-data";

    @Autowired
    private InvalidDataService invalidDataService;

    @Override
    public String getProtocolName() {
        return PROTOCOL_NAME;
    }

    @Override
    public ResourceRequest validAndParseParam(ResourceRequest resourceRequest, boolean isWrite) {
        return resourceRequest;
    }

    @Override
    public PermissionInfo getPermissionInfo(ResourceRequest resourceRequest, ResourceRequest param) {
        return PermissionInfo.builder()
                .isWritable(false)
                .isReadable(true)
                .build();
    }

    @Override
    public Resource getFileResource(ResourceRequest resourceRequest, ResourceRequest param) throws IOException {
        Long id = Long.parseLong(param.getTargetId());
        return invalidDataService.getDownloadResource(id).getBody();
    }

    @Override
    public String getPathMappingIdentity(ResourceRequest resourceRequest, ResourceRequest param) {
        return SecureUtils.getMd5(PROTOCOL_NAME + ":" + param.getTargetId());
    }
}
