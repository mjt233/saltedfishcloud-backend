package com.sfc.dm.service.identify;

import com.sfc.dm.model.dto.FileMetadataDefine;
import com.sfc.dm.model.dto.FileTypeCheckResultDetail;
import com.saltedfishcloud.ext.ve.core.FFMpegHelper;
import com.saltedfishcloud.ext.ve.model.FormatInfo;
import com.saltedfishcloud.ext.ve.model.StreamInfo;
import com.saltedfishcloud.ext.ve.model.VideoInfo;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.*;

/**
 * 视频文件类型识别提供者
 */
@Slf4j
public class VideoCheckProvider implements FileTypeCheckProvider {
    private static final String ID = "videoCheckProvider";
    private static final String TYPE_NAME = "视频";
    private static final String TYPE_ID = "video";
    private static final List<String> EXTENSIONS = List.of(".mp4", ".avi", ".mkv", ".mov", ".wmv", ".flv", ".webm", ".m4v", ".ts");

    /**
     * FFMpegHelper实例，用于通过ffprobe提取视频元数据。
     * 使用Object类型避免FFMpegHelper类不在classpath时的NoClassDefFoundError。
     */
    private final Object ffMpegHelper;

    /**
     * @param ffMpegHelper FFMpegHelper实例，null表示ffprobe不可用
     */
    public VideoCheckProvider(Object ffMpegHelper) {
        this.ffMpegHelper = ffMpegHelper;
    }

    /**
     * 创建不带FFMpegHelper的Provider，仅使用手动解析提取元数据
     */
    public VideoCheckProvider() {
        this(null);
    }

    @Override
    public String getId() { return ID; }

    @Override
    public String getTypeName() { return TYPE_NAME; }

    @Override
    public String getTypeId() { return TYPE_ID; }

    @Override
    public List<String> getSupportedFileExtensions() { return EXTENSIONS; }

    @Override
    public List<FileMetadataDefine> getMetadataDefines() {
        return List.of(
                new FileMetadataDefine("时长", "duration", "视频时长（秒）", "span"),
                new FileMetadataDefine("宽度", "width", "视频宽度（像素）", "span"),
                new FileMetadataDefine("高度", "height", "视频高度（像素）", "span"),
                new FileMetadataDefine("视频编码器", "videoCodec", "视频编码器名称（如h264, hevc）", "span"),
                new FileMetadataDefine("音频编码器", "audioCodec", "音频编码器名称（如aac, mp3）", "span"),
                new FileMetadataDefine("封装格式", "containerFormat", "视频封装格式（如mov,mp4,m4a,3gp,3g2,mj2）", "span")
        );
    }

    @Override
    public FileTypeCheckResultDetail checkFile(File file, boolean extraMetadata) {
        String fileName = file.getName().toLowerCase();
        FileTypeCheckResultDetail detail = new FileTypeCheckResultDetail();

        // 检测文件头
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            if (isMp4(raf)) {
                detail.setExtension(".mp4");
                detail.setMimetype("video/mp4");
            } else if (isAvi(raf)) {
                detail.setExtension(".avi");
                detail.setMimetype("video/avi");
            } else if (isMkv(raf)) {
                detail.setExtension(".mkv");
                detail.setMimetype("video/x-matroska");
            } else if (isWebm(raf)) {
                detail.setExtension(".webm");
                detail.setMimetype("video/webm");
            }
        } catch (IOException e) {
            log.debug("视频文件头读取失败: {}", file.getName(), e);
        }

        // 无法通过文件头识别，根据扩展名兜底
        if (detail.getExtension() == null) {
            for (String ext : EXTENSIONS) {
                if (fileName.endsWith(ext)) {
                    detail.setExtension(ext);
                    break;
                }
            }
        }

        if (detail.getExtension() == null) {
            return null;
        }

        // 提取元数据
        if (extraMetadata) {
            Map<String, String> metadata = extractMetadata(file);
            if (metadata != null && !metadata.isEmpty()) {
                detail.setMetadata(metadata);
            }
        }

        return detail;
    }

    /**
     * 检测MP4文件头（ftyp box）
     */
    private boolean isMp4(RandomAccessFile raf) throws IOException {
        raf.seek(4);
        byte[] ftyp = new byte[4];
        raf.readFully(ftyp);
        return ftyp[0] == 'f' && ftyp[1] == 't' && ftyp[2] == 'y' && ftyp[3] == 'p';
    }

    /**
     * 检测AVI文件头（RIFF....AVI）
     */
    private boolean isAvi(RandomAccessFile raf) throws IOException {
        raf.seek(0);
        byte[] header = new byte[12];
        if (raf.read(header) < 12) return false;
        return header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'A' && header[9] == 'V' && header[10] == 'I' && header[11] == ' ';
    }

    /**
     * 检测MKV文件头（0x1A45DFA3 EBML magic）
     */
    private boolean isMkv(RandomAccessFile raf) throws IOException {
        raf.seek(0);
        byte[] header = new byte[4];
        if (raf.read(header) < 4) return false;
        return (header[0] & 0xFF) == 0x1A && (header[1] & 0xFF) == 0x45
                && (header[2] & 0xFF) == 0xDF && (header[3] & 0xFF) == 0xA3;
    }

    /**
     * 检测WebM文件头（也是EBML，但扩展名不同）
     */
    private boolean isWebm(RandomAccessFile raf) throws IOException {
        return isMkv(raf);
    }

    /**
     * 提取视频元数据，优先使用ffprobe，失败时回退到手动MP4解析
     */
    private Map<String, String> extractMetadata(File file) {
        if (ffMpegHelper != null) {
            try {
                return extractMetadataViaFFProbe(file);
            } catch (NoClassDefFoundError e) {
                log.debug("FFMpegHelper类不可用，回退到手动解析");
            } catch (Exception e) {
                log.debug("ffprobe元数据提取失败，回退到手动解析: {}", file.getName(), e);
            }
        }
        return parseMp4Metadata(file);
    }

    /**
     * 通过ffprobe提取视频元数据（编码器、封装格式、分辨率、时长等）
     */
    private Map<String, String> extractMetadataViaFFProbe(File file) throws IOException {
        FFMpegHelper helper = (FFMpegHelper) this.ffMpegHelper;
        VideoInfo videoInfo = helper.getVideoInfo(file.getAbsolutePath());

        Map<String, String> metadata = new HashMap<>();

        // 视频流：编码器、宽高
        for (StreamInfo stream : videoInfo.getStreams()) {
            if ("video".equals(stream.getCodecType())) {
                if (stream.getCodecName() != null) {
                    metadata.put("videoCodec", stream.getCodecName());
                }
                if (stream.getWidth() != null) {
                    metadata.put("width", String.valueOf(stream.getWidth()));
                }
                if (stream.getHeight() != null) {
                    metadata.put("height", String.valueOf(stream.getHeight()));
                }
                break;
            }
        }

        // 音频流：编码器
        for (StreamInfo stream : videoInfo.getStreams()) {
            if ("audio".equals(stream.getCodecType())) {
                if (stream.getCodecName() != null) {
                    metadata.put("audioCodec", stream.getCodecName());
                }
                break;
            }
        }

        // 封装格式、时长
        FormatInfo format = videoInfo.getFormat();
        if (format != null) {
            if (format.getFormatName() != null) {
                metadata.put("containerFormat", format.getFormatName());
            }
            if (format.getDuration() != null) {
                metadata.put("duration", String.format("%.1f", format.getDuration()));
            }
        }

        return metadata.isEmpty() ? null : metadata;
    }

    /**
     * 手动解析MP4元数据（简化版：尝试从moov box读取时长）
     */
    private Map<String, String> parseMp4Metadata(File file) {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            if (!isMp4(raf)) {
                return null;
            }
            Map<String, String> metadata = new HashMap<>();
            raf.seek(0);
            long fileLength = raf.length();
            while (raf.getFilePointer() < fileLength - 8) {
                long boxStart = raf.getFilePointer();
                int boxSize = raf.readInt();
                byte[] boxType = new byte[4];
                raf.readFully(boxType);
                String type = new String(boxType);
                if (boxSize < 8) break;
                if ("moov".equals(type)) {
                    parseMoovBox(raf, boxStart + boxSize, metadata);
                    break;
                }
                raf.seek(boxStart + boxSize);
            }
            return metadata.isEmpty() ? null : metadata;
        } catch (Exception e) {
            log.debug("MP4元数据解析失败", e);
            return null;
        }
    }

    /**
     * 解析moov box内的mvhd获取时长
     */
    private void parseMoovBox(RandomAccessFile raf, long moovEnd, Map<String, String> metadata) throws IOException {
        while (raf.getFilePointer() < moovEnd - 8) {
            long boxStart = raf.getFilePointer();
            int boxSize = raf.readInt();
            byte[] boxType = new byte[4];
            raf.readFully(boxType);
            String type = new String(boxType);

            if (boxSize < 8) break;

            if ("mvhd".equals(type)) {
                parseMvhdBox(raf, boxStart + boxSize, metadata);
                return;
            }
            raf.seek(boxStart + boxSize);
        }
    }

    /**
     * 解析mvhd box获取时长
     */
    private void parseMvhdBox(RandomAccessFile raf, long mvhdEnd, Map<String, String> metadata) throws IOException {
        int version = raf.readUnsignedByte();
        raf.skipBytes(3); // flags

        if (version == 0) {
            raf.skipBytes(4 + 4); // creation_time, modification_time
            int timescale = raf.readInt();
            int duration = raf.readInt();
            if (timescale > 0) {
                double seconds = (double) duration / timescale;
                metadata.put("duration", String.format("%.1f", seconds));
            }
        } else {
            raf.skipBytes(8 + 8); // creation_time, modification_time
            int timescale = raf.readInt();
            long duration = raf.readLong();
            if (timescale > 0) {
                double seconds = (double) duration / timescale;
                metadata.put("duration", String.format("%.1f", seconds));
            }
        }
    }
}
