package com.saltedfishcloud.ext.ve.service;

import com.saltedfishcloud.ext.ve.constant.VEConstants;
import com.saltedfishcloud.ext.ve.model.ProcessWrap;
import com.saltedfishcloud.ext.ve.model.StreamInfo;
import com.saltedfishcloud.ext.ve.model.VideoInfo;
import com.xiaotao.saltedfishcloud.common.ResponseResource;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.exception.UnsupportedProtocolException;
import com.xiaotao.saltedfishcloud.model.dto.ResourceRequest;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.StoreServiceFactory;
import com.xiaotao.saltedfishcloud.service.file.TempStoreService;
import com.xiaotao.saltedfishcloud.service.file.store.attach.AttachStorage;
import com.xiaotao.saltedfishcloud.service.file.store.attach.AttachStorageDomainDefinition;
import com.xiaotao.saltedfishcloud.service.file.store.attach.AttachStorageManager;
import com.xiaotao.saltedfishcloud.service.resource.ResourceService;
import com.xiaotao.saltedfishcloud.utils.*;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;


@Service
@RequiredArgsConstructor
@Slf4j
public class VideoSubtitleService {
    private final ResourceService resourceService;
    private final VideoService videoService;
    private final StoreServiceFactory storeServiceFactory;

    private final Semaphore semaphore = new Semaphore(
            Math.max(1, Runtime.getRuntime().availableProcessors() - 1)
    );
    private final static Set<String> SUBTITLE_BIT_ENCODERS = Set.of("hdmv_pgs_subtitle", "dvb_subtitle", "dvd_subtitle");

    private AttachStorage attachStorage;

    @Autowired
    public void setAttachStorageManager(AttachStorageManager attachStorageManager) {
        attachStorageManager.registerStorageDomain(AttachStorageDomainDefinition.builder()
                        .name("视频元数据")
                        .id("ve")
                .build());
        attachStorage = attachStorageManager.getStorage("ve");
    }

    @Validated
    public Resource getSubtitleResource(ResourceRequest videoResourceRequest, @NotBlank String streamIndex) throws UnsupportedProtocolException, IOException {

        // 获取视频资源
        Resource resource = resourceService.getResource(videoResourceRequest);

        // 获取视频信息并提取指定的流信息，并验证所选流是否为字幕流
        VideoInfo videoInfo = videoService.getVideoInfo(resource);
        StreamInfo streamInfo = videoInfo.getStreams().get(Integer.parseInt(streamIndex));
        if (!VEConstants.EncoderType.SUBTITLE.equals(streamInfo.getCodecType())) {
            throw new IllegalArgumentException("stream index " + streamIndex + " is " + streamInfo.getCodecType() + " not a subtitle stream");
        }

        // 根据字幕编码格式确定输出格式和Content-Type
        String format;
        String contentType;
        String encoder;
        String ffmpegFormat;
        if (SUBTITLE_BIT_ENCODERS.contains(streamInfo.getCodecName())) {
            // 位图字幕格式（如SUP）
            format = VEConstants.SubtitleType.SUP;
            contentType = "application/x-sup";
            encoder = "copy";
            ffmpegFormat = null;
        } else if (VEConstants.SubtitleType.ASS.equals(streamInfo.getCodecName()) || VEConstants.SubtitleType.SSA.equals(streamInfo.getCodecName())) {
            // ASS/SSA字幕格式
            format = VEConstants.SubtitleType.ASS;
            contentType = "text/x-ass";
            encoder = "copy";
            ffmpegFormat = "ass";
        } else {
            // 默认转换为WEBVTT格式（Web标准字幕格式）
            format = VEConstants.SubtitleType.WEBVTT;
            contentType = "text/vtt";
            encoder = "webvtt";
            ffmpegFormat = "webvtt";
        }

        String cacheFileName;

        // 生成缓存文件名（基于文件特征或资源特征）
        if (resource.isFile()) {

            // 本地文件：使用文件路径、修改时间和大小生成MD5
            File file = resource.getFile();
            cacheFileName = SecureUtils.getMd5(file.getPath() + "_" + file.lastModified() + "_" + file.length())
                    + "_" + streamIndex + "." + format;
        } else {

            // 远程资源：使用路径、文件名、修改时间和内容长度生成MD5
            cacheFileName = SecureUtils.getMd5(
                    StringUtils.appendPath(videoResourceRequest.getPath(), resource.getFilename()) + "_"
                            + resource.lastModified() + "_"
                            + resource.contentLength())
                    + "_" + streamIndex + "." + format;
        }

        // 快速检查是否有缓存
        String cachePath = "subtitle/" + cacheFileName;
        if (attachStorage.exist(cachePath)) {
            Resource cacheResource = attachStorage.getFile(cachePath).orElse(null);
            if (cacheResource != null) {
                return ResponseResource.create(cacheResource)
                        .setResponseFilename(FileUtils.parseName(videoResourceRequest.getName())[0] + "." + format)
                        .setContentType(contentType);
            }
            log.warn("字幕缓存路径存在，但无法获取缓存数据 {}", cachePath);
        }

        // 无缓存，加锁后重新检查，若依然无缓存则执行字幕提取
        return LockUtils.execute(cacheFileName, () -> {
            try {
                if (!semaphore.tryAcquire(1, 10, TimeUnit.MINUTES)) {
                    throw new RuntimeException("subtitle extract service busy");
                }
                try {
                    Resource output;
                    if (!attachStorage.exist(cachePath)) {
                        Path tmpFilePath = PathUtils.getTempPath().resolve(cacheFileName);
                        String tmpFile = tmpFilePath.toString();
                        try {
                            this.generateSubtitleFile(resource, tmpFile, streamIndex, ffmpegFormat, encoder);
                            attachStorage.saveFile(cachePath, new PathResource(tmpFilePath));
                        } finally {
                            Files.deleteIfExists(tmpFilePath);
                        }
                    }
                    output = attachStorage.getFile(cachePath).orElseThrow(() -> new JsonException(500, "字幕缓存文件丢失 " + cachePath));
                    return ResponseResource.create(output)
                            .setResponseFilename(FileUtils.parseName(videoResourceRequest.getName())[0] + "." + format)
                            .setContentType(contentType);
                } finally {
                    semaphore.release();
                }
            } catch (InterruptedException e) {
                log.error("字幕提取操作取消", e);
                throw new RuntimeException("action interrupted");
            }
        });

    }


    private void generateSubtitleFile(Resource videoResource, String savePath, String streamIndex, String ffmpegFormat, String encoder) throws IOException {
        ProcessWrap wrap = videoService.extractStream(videoResource, savePath, streamIndex, ffmpegFormat, encoder, VEConstants.EncoderType.SUBTITLE);
        String errMsg = wrap.waitProcess();
        if (errMsg != null) {
            log.error("FFmpeg调用出错: 执行命令: {}\nffmepg 输出:\n {}", String.join(" ", wrap.getArgs()), errMsg);
            throw new RuntimeException("字幕获取失败");
        }
    }
}
