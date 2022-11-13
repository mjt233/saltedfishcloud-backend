package com.xiaotao.saltedfishcloud.service.share;

import com.xiaotao.saltedfishcloud.constant.ResourceProtocol;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.model.dto.ResourceRequest;
import com.xiaotao.saltedfishcloud.service.resource.ResourceProtocolHandler;
import com.xiaotao.saltedfishcloud.service.resource.ResourceService;
import com.xiaotao.saltedfishcloud.service.share.entity.ShareExtractorDTO;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 为统一资源访问接口添加文件分享的资源支持
 */
@Component
public class ShareResourceHandler implements ResourceProtocolHandler, InitializingBean {
    @Autowired
    private ResourceService resourceService;
    @Autowired
    private ShareService shareService;

    @Override
    public void afterPropertiesSet() throws Exception {
        resourceService.addResourceHandler(this);
    }

    @Override
    public Resource getFileResource(ResourceRequest param) throws IOException {
        Object vid = param.getParams().get("vid");
        if (vid == null) {
            throw new JsonException("缺少参数：vid");
        }
        String code = param.getParams().getOrDefault("code", "").toString();
        return shareService.getFileResource(ShareExtractorDTO.builder()
                        .code(code)
                        .isThumbnail(param.getIsThumbnail())
                        .name(param.getName())
                        .path(param.getPath())
                        .verification(vid.toString())
                        .sid(Integer.parseInt(param.getTargetId()))
                        .build()
        );
    }

    @Override
    public String getProtocolName() {
        return ResourceProtocol.SHARE;
    }
}
