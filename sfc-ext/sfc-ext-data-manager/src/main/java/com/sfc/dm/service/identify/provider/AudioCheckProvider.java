package com.sfc.dm.service.identify.provider;

import com.sfc.dm.model.dto.FileMetadataDefine;
import com.sfc.dm.model.dto.FileTypeInfo;
import com.sfc.dm.model.dto.FileTypeCheckResultDetail;
import com.sfc.dm.service.identify.FileTypeCheckProvider;
import com.saltedfishcloud.ext.ve.core.FFMpegHelper;
import com.saltedfishcloud.ext.ve.model.FormatInfo;
import com.saltedfishcloud.ext.ve.model.StreamInfo;
import com.saltedfishcloud.ext.ve.model.VideoInfo;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.*;

/**
 * 音频文件类型识别提供者，通过ffprobe检测文件格式并提取音频元数据
 */
@Slf4j
public class AudioCheckProvider implements FileTypeCheckProvider {
    private static final String ID = "audioCheckProvider";
    private static final String TYPE_NAME = "音频";
    private static final String TYPE_ID = "audio";
    private static final List<String> EXTENSIONS = List.of(".mp3", ".aac", ".flac", ".wav", ".ogg", ".wma", ".m4a", ".opus");

    private final FFMpegHelper ffMpegHelper;

    /**
     * @param ffMpegHelper FFMpegHelper实例，用于通过ffprobe检测音频格式和提取元数据
     */
    public AudioCheckProvider(FFMpegHelper ffMpegHelper) {
        this.ffMpegHelper = ffMpegHelper;
    }

    @Override
    public String getId() { return ID; }

    @Override
    public List<FileTypeInfo> getTypeInfoList() {
        return List.of(new FileTypeInfo(TYPE_ID, TYPE_NAME, List.of(
                new FileMetadataDefine("歌曲名称", "title", "音频文件标题", "span"),
                new FileMetadataDefine("歌手", "artist", "歌手/艺术家", "span"),
                new FileMetadataDefine("专辑", "album", "专辑名称", "span"),
                new FileMetadataDefine("时长", "duration", "音频时长（秒）", "span"),
                new FileMetadataDefine("编码器", "audioCodec", "音频编码器名称（如mp3, aac, flac）", "span"),
                new FileMetadataDefine("封装格式", "containerFormat", "音频封装格式（如mp3, flac, wav）", "span"),
                new FileMetadataDefine("采样率", "sampleRate", "音频采样率（Hz）", "span"),
                new FileMetadataDefine("声道数", "channels", "音频声道数", "span"),
                new FileMetadataDefine("码率", "bitRate", "音频码率（bps）", "span")
        )));
    }

    @Override
    public List<String> getSupportedFileExtensions() { return EXTENSIONS; }

    @Override
    public FileTypeCheckResultDetail checkFile(File file, boolean extraMetadata) {
        try {
            VideoInfo videoInfo = ffMpegHelper.getVideoInfo(file.getAbsolutePath());
            FormatInfo format = videoInfo.getFormat();
            if (format == null || format.getFormatName() == null) {
                return null;
            }

            // 确认包含音频流
            boolean hasAudio = videoInfo.getStreams().stream()
                    .anyMatch(s -> "audio".equals(s.getCodecType()));
            if (!hasAudio) {
                return null;
            }

            // 根据ffprobe返回的formatName映射扩展名和MIME类型
            String[] extAndMime = mapFormat(format.getFormatName());
            if (extAndMime == null) {
                return null;
            }

            FileTypeCheckResultDetail detail = new FileTypeCheckResultDetail();
            detail.setExtension(extAndMime[0]);
            detail.setMimetype(extAndMime[1]);

            if (extraMetadata) {
                Map<String, String> metadata = extractMetadata(videoInfo);
                if (metadata != null && !metadata.isEmpty()) {
                    detail.setMetadata(metadata);
                }
            }

            return detail;
        } catch (Exception e) {
            log.debug("ffprobe音频检测失败: {}", file.getName(), e);
            return null;
        }
    }

    /**
     * 将ffprobe的formatName映射为扩展名和MIME类型
     *
     * @return [extension, mimetype]，无法识别时返回null
     */
    private String[] mapFormat(String formatName) {
        if (formatName.contains("mp3")) {
            return new String[]{".mp3", "audio/mpeg"};
        }
        if (formatName.contains("flac")) {
            return new String[]{".flac", "audio/flac"};
        }
        if (formatName.contains("wav")) {
            return new String[]{".wav", "audio/wav"};
        }
        if (formatName.contains("ogg") || formatName.contains("opus")) {
            return new String[]{".ogg", "audio/ogg"};
        }
        if (formatName.contains("aac") || formatName.contains("adts")) {
            return new String[]{".aac", "audio/aac"};
        }
        if (formatName.contains("asf")) {
            return new String[]{".wma", "audio/x-ms-wma"};
        }
        if (formatName.contains("mp4") || formatName.contains("mov") || formatName.contains("m4a")) {
            return new String[]{".m4a", "audio/mp4"};
        }
        return null;
    }

    /**
     * 通过ffprobe提取音频元数据（编码器、封装格式、采样率、声道数、标签等）
     */
    private Map<String, String> extractMetadata(VideoInfo videoInfo) {
        Map<String, String> metadata = new HashMap<>();

        // 音频流：编码器、采样率、声道数
        for (StreamInfo stream : videoInfo.getStreams()) {
            if ("audio".equals(stream.getCodecType())) {
                if (stream.getCodecName() != null) {
                    metadata.put("audioCodec", stream.getCodecName());
                }
                if (stream.getSampleRate() != null) {
                    metadata.put("sampleRate", stream.getSampleRate());
                }
                if (stream.getChannels() != null) {
                    metadata.put("channels", String.valueOf(stream.getChannels()));
                }
                if (stream.getBitRate() != null) {
                    metadata.put("bitRate", String.valueOf(stream.getBitRate()));
                }
                break;
            }
        }

        // 封装格式、时长、标签信息
        FormatInfo format = videoInfo.getFormat();
        if (format != null) {
            if (format.getFormatName() != null) {
                metadata.put("containerFormat", format.getFormatName());
            }
            if (format.getDuration() != null) {
                metadata.put("duration", String.format("%.1f", format.getDuration()));
            }
            Map<String, String> tags = format.getTags();
            if (tags != null) {
                if (tags.get("title") != null) metadata.put("title", tags.get("title"));
                if (tags.get("artist") != null) metadata.put("artist", tags.get("artist"));
                if (tags.get("album") != null) metadata.put("album", tags.get("album"));
            }
        }

        return metadata.isEmpty() ? null : metadata;
    }
}
