package com.saltedfishcloud.ext.ve.service;

import com.saltedfishcloud.ext.ve.constant.VEConstants;
import com.xiaotao.saltedfishcloud.model.dto.ResourceRequest;
import com.xiaotao.saltedfishcloud.service.resource.ResourceProtocolHandler;
import com.xiaotao.saltedfishcloud.utils.ResourceUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.Objects;

public class SubtitleResourceHandler implements ResourceProtocolHandler {


    @Autowired
    private VideoService videoService;


    @Override
    public Resource getFileResource(ResourceRequest param) throws IOException {
        Resource resource = videoService.getResource(param);
        String subtitleText;

        String stream = Objects.requireNonNull(param.getParams().get("stream"), "param.stream不能为空");
        String subtitleType = param.getParams().getOrDefault("type", VEConstants.SUBTITLE_TYPE.WEBVTT);
        subtitleText = videoService.getSubtitleText(resource, stream, subtitleType);
        return ResourceUtils.stringToResource(subtitleText)
                .setContentType("text/vtt");

    }

    @Override
    public String getProtocolName() {
        return "subtitle";
    }
}
