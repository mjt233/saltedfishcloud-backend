package com.saltedfishcloud.ext.ve.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class VideoInfo {
    private List<StreamInfo> streams = new ArrayList<>(0);
    private List<ChapterInfo> chapters = new ArrayList<>(0);
}
