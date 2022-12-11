package com.saltedfishcloud.ext.ve.service;

import com.xiaotao.saltedfishcloud.model.dto.ResourceRequest;
import com.xiaotao.saltedfishcloud.service.resource.ResourceProtocolHandler;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import com.xiaotao.saltedfishcloud.utils.ResourceUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;

import java.io.IOException;

public class VideoInfoResourceHandler implements ResourceProtocolHandler {

    @Autowired
    private VideoService videoService;


    @Override
    public Resource getFileResource(ResourceRequest param) throws IOException {
        Resource resource = videoService.getResource(param);
        return ResourceUtils.stringToResource(MapperHolder.toJson(videoService.getVideoInfo(resource)))
                .setContentType("application/json;charset=utf-8");
    }

    @Override
    public String getProtocolName() {
        return "videoInfo";
    }
}
