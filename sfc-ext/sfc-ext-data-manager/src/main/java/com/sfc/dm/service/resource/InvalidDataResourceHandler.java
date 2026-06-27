package com.sfc.dm.service.resource;

import com.sfc.dm.model.po.InvalidDataRecord;
import com.sfc.dm.repo.InvalidDataRecordRepo;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.model.PermissionInfo;
import com.xiaotao.saltedfishcloud.model.dto.ResourceRequest;
import com.xiaotao.saltedfishcloud.service.file.StoreServiceFactory;
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
    private InvalidDataRecordRepo invalidDataRecordRepo;

    @Autowired
    private StoreServiceFactory storeServiceFactory;

    @Override
    public String getProtocolName() {
        return PROTOCOL_NAME;
    }

    @Override
    public ResourceRequest validAndParseParam(ResourceRequest resourceRequest, boolean isWrite) {
        String targetId = resourceRequest.getTargetId();
        try {
            Long.parseLong(targetId);
        } catch (NumberFormatException e) {
            throw new JsonException(400, "无效的失效数据记录ID");
        }
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
        InvalidDataRecord record = invalidDataRecordRepo.findById(id)
                .orElseThrow(() -> new JsonException(404, "失效数据记录不存在"));

        Resource resource = storeServiceFactory.getService().getStorageProvider().getResource(record.getStoragePath());
        if (resource == null) {
            throw new JsonException(404, "物理存储文件不存在");
        }
        return resource;
    }

    @Override
    public String getPathMappingIdentity(ResourceRequest resourceRequest, ResourceRequest param) {
        return SecureUtils.getMd5(PROTOCOL_NAME + ":" + param.getTargetId());
    }
}
