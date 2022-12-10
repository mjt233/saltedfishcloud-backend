package com.saltedfishcloud.ext.ve.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class VideoInfo {
    /**
     * 媒体流列表
     */
    @JsonIgnore
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
     * 字幕流列表
     */
    private List<SubtitleStream> subtitleStreamList = new ArrayList<>();

    private List<MediaStream> otherStreamList = new ArrayList<>();

    /**
     * 章节标记
     */
    private List<Chapter> chapterList = new ArrayList<>();

    public void addChapter(Chapter chapter) {
        this.chapterList.add(chapter);
    }

    public void addStream(MediaStream stream) {
        if (stream.isAudio()) {
            AudioStream audioStream = stream.toAudioStream();
            mediaStreamList.add(audioStream);
            audioStreamList.add(audioStream);
        } else if (stream.isVideo()) {
            VideoStream videoStream = stream.toVideoStream();
            mediaStreamList.add(videoStream);
            videoStreamList.add(videoStream);
        } else if (stream.isSubtitle()) {
            SubtitleStream subtitleStream = stream.toSubtitleStream();
            mediaStreamList.add(subtitleStream);
            subtitleStreamList.add(subtitleStream);
        } else {
            mediaStreamList.add(stream);
            otherStreamList.add(stream);
        }
    }


}
