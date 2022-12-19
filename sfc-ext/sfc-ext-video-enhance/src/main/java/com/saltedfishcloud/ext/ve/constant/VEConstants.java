package com.saltedfishcloud.ext.ve.constant;

public interface VEConstants {
    String PROPERTY_KEY = "ve.property";

    interface SubtitleType {
        String WEBVTT = "webvtt";
        String SRT = "srt";
        String ASS = "ass";
    }

    interface EncoderType {
        String VIDEO = "video";
        String AUDIO = "audio";
        String SUBTITLE = "subtitle";
        String OTHER = "other";
    }
}
