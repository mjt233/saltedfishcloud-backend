package com.saltedfishcloud.ext.ve.service;

import com.saltedfishcloud.ext.ve.constant.VEConstants;
import com.saltedfishcloud.ext.ve.core.FFMpegHelper;
import com.saltedfishcloud.ext.ve.model.VideoInfo;
import com.xiaotao.saltedfishcloud.exception.UnsupportedProtocolException;
import com.xiaotao.saltedfishcloud.model.dto.ResourceRequest;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.service.resource.ResourceService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;

@Service
public class VideoService {
    @Autowired
    private FFMpegHelper ffMpegHelper;

    @Autowired
    @Lazy
    private ResourceService resourceService;

    @Autowired
    private DiskFileSystemManager diskFileSystemManager;

    /**
     * 提取资源拓展方法，支持通过sourceProtocol和sourceId获取资源的依赖资源
     */
    public Resource getResource(ResourceRequest param) throws IOException {
        String sourceProtocol = param.getParams().get("sourceProtocol");
        Resource resource;

        // 若指定了文件来源协议，则从来源协议处理器中获取资源（如：从文件分享中获取，或是其他拓展的协议）
        if (sourceProtocol != null) {
            if (sourceProtocol.equals(param.getProtocol())) {
                throw new IllegalArgumentException("来源协议不能与请求协议相同");
            }
            String sourceId = param.getParams().get("sourceId");
            if (sourceId == null) {
                throw new IllegalArgumentException("缺少sourceId");
            }
            ResourceRequest nextRequest = new ResourceRequest();
            BeanUtils.copyProperties(param, nextRequest);
            nextRequest.setParams(new HashMap<>(param.getParams()));
            nextRequest.setProtocol(sourceProtocol);
            nextRequest.setTargetId(sourceId);
            try {
                resource = resourceService.getResource(nextRequest);
            } catch (UnsupportedProtocolException e) {
                throw new RuntimeException(e);
            }
        } else {
            resource = diskFileSystemManager.getMainFileSystem().getResource(Integer.parseInt(param.getTargetId()), param.getPath(), param.getName());
        }
        return resource;
    }

    private String resourceToLocalPath(Resource resource) {
        if (resource == null) {
            throw new IllegalArgumentException("资源为null");
        }
        if (!(resource instanceof PathResource)) {
            throw new IllegalArgumentException("目前仅支持PathResource");
        }
        return ((PathResource) resource).getPath();
    }

    /**
     * 获取字幕信息列表
     * @param resource  视频文件资源
     */
    public VideoInfo getVideoInfo(Resource resource) throws IOException {
        String localPath = resourceToLocalPath(resource);
        return ffMpegHelper.getVideoInfo(localPath);
    }

    /**
     * 获取字幕内容
     * @param resource  视频文件资源
     * @param streamNo  字幕流编号
     * @param type      字幕类型
     */
    public String getSubtitleText(Resource resource, String streamNo, String type) throws IOException {
        if (type == null) {
            type = VEConstants.SUBTITLE_TYPE.WEBVTT;
        }
        if (streamNo == null) {
            throw new IllegalArgumentException("流编号不能为空");
        }
        String localPath = resourceToLocalPath(resource);
        return ffMpegHelper.extractSubtitle(localPath, streamNo, type);
    }
}
