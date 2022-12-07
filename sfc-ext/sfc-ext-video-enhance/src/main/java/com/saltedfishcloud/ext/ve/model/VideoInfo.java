package com.saltedfishcloud.ext.ve.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class VideoInfo {
    /**
     * 媒体流列表
     */
    private List<MediaStream> mediaStreamList = new ArrayList<>();

    /**
     * 音频流列表
     */
    private List<AudioStream> audioStreamList = new ArrayList<>();

    /**
     * 视频流列表
     */
    private List<VideoStream> videoStreamList = new ArrayList<>();

    /**
     * 章节标记
     */
    private List<Chapter> chapterList = new ArrayList<>();

    public void addChapter(Chapter chapter) {
        this.chapterList.add(chapter);
    }

    public void addStream(MediaStream stream) {
        this.mediaStreamList.add(stream);
    }
}
