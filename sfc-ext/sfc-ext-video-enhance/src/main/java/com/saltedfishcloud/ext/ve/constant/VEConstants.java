package com.saltedfishcloud.ext.ve.constant;

public interface VEConstants {
    /**
     * 插件配置key
     */
    String PROPERTY_KEY = "ve.property";

    /**
     * 字幕类型
     */
    interface SubtitleType {
        String WEBVTT = "webvtt";
        String SRT = "srt";
        String ASS = "ass";
    }

    /**
     * 编码器类型
     */
    interface EncoderType {
        String VIDEO = "video";
        String AUDIO = "audio";
        String SUBTITLE = "subtitle";
        String OTHER = "other";
    }

    /**
     * 编码方式
     */
    interface EncodeMethod {
        /**
         * 转换
         */
        String CONVERT = "convert";
        /**
         * 流复制
         */
        String COPY = "copy";
    }

    /**
     * 编码转换任务侧重类型
     */
    interface ConvertTaskType {
        /**
         * 涉及到视频编码转换类型
         */
        String VIDEO = "video";

        /**
         * 音频重编码类型
         */
        String AUDIO = "audio";

        /**
         * 仅转封装或流复制
         */
        String FORMAT = "format";
    }

    interface TaskStatus {
        int WAITING = 0;

        int RUNNING = 1;

        int SUCCESS = 2;

        int FAILED = 3;
    }

    /**
     * 视频编码转换的异步任务类型
     */
    String TASK_TYPE = "video-convert";


}
