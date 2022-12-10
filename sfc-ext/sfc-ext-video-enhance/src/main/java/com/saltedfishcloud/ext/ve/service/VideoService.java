package com.saltedfishcloud.ext.ve.service;

import com.saltedfishcloud.ext.ve.core.FFMpegHelper;
import com.saltedfishcloud.ext.ve.model.SubtitleStream;
import com.saltedfishcloud.ext.ve.model.VideoInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
public class VideoService {
    @Autowired
    private FFMpegHelper ffMpegHelper;

    private String resourceToLocalPath(Resource resource) {
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
        String localPath = resourceToLocalPath(resource);
        return ffMpegHelper.extractSubtitle(localPath, streamNo, type);
    }
}
