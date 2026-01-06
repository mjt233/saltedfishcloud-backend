package com.saltedfishcloud.ext.ve.service;

import com.saltedfishcloud.ext.ve.constant.VEConstants;
import com.saltedfishcloud.ext.ve.model.ProcessWrap;
import com.saltedfishcloud.ext.ve.model.StreamInfo;
import com.saltedfishcloud.ext.ve.model.VideoInfo;
import com.xiaotao.saltedfishcloud.common.ResponseResource;
import com.xiaotao.saltedfishcloud.constant.ResourceProtocol;
import com.xiaotao.saltedfishcloud.model.PermissionInfo;
import com.xiaotao.saltedfishcloud.model.dto.ResourceRequest;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.StoreServiceFactory;
import com.xiaotao.saltedfishcloud.service.file.TempStoreService;
import com.xiaotao.saltedfishcloud.service.resource.AbstractResourceProtocolHandler;
import com.xiaotao.saltedfishcloud.service.resource.ResourceService;
import com.xiaotao.saltedfishcloud.utils.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

@Slf4j
public class SubtitleResourceHandler extends AbstractResourceProtocolHandler<ResourceRequest> {

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private VideoService videoService;

    @Autowired
    private StoreServiceFactory storeServiceFactory;

    private final static Set<String> SUBTITLE_BIT_ENCODERS = Set.of("hdmv_pgs_subtitle", "dvb_subtitle", "dvd_subtitle");

    @Override
    public ResourceRequest validAndParseParam(ResourceRequest resourceRequest, boolean isWrite) {
        return resourceRequest;
    }

    @Override
    public String getPathMappingIdentity(ResourceRequest resourceRequest, ResourceRequest param) {
        ResourceRequest sourceResourceRequest = videoService.getSourceResourceRequest(param);
        if (sourceResourceRequest != param) {
            return resourceService.getResourceHandler(sourceResourceRequest.getProtocol()).getPathMappingIdentity(sourceResourceRequest) + "#" + getProtocolName();
        } else {
            return resourceService.getResourceHandler(ResourceProtocol.MAIN).getPathMappingIdentity(param) + "#" + getProtocolName();
        }
    }

    @Override
    public PermissionInfo getPermissionInfo(ResourceRequest resourceRequest, ResourceRequest param) {
        PermissionInfo permissionInfo = resourceService.getResourceHandler(param.getProtocol())
                .getPermissionInfo(videoService.getSourceResourceRequest(param));
        return PermissionInfo.builder()
                .isReadable(permissionInfo.isReadable())
                .isWritable(false)
                .ownerUid(permissionInfo.getOwnerUid())
                .build();
    }

    @Override
    public Resource getFileResource(ResourceRequest resourceRequest, ResourceRequest param) throws IOException {
        // 获取视频资源
        Resource resource = videoService.getResource(param);
        // 从请求参数中获取字幕流索引，并进行非空校验
        String streamIndex = Objects.requireNonNull(TypeUtils.toString(param.getParams().get("stream")), "param.stream不能为空");

        // 获取视频信息并提取指定的流信息，并验证所选流是否为字幕流
        VideoInfo videoInfo = videoService.getVideoInfo(resource);
        StreamInfo streamInfo = videoInfo.getStreams().get(Integer.parseInt(streamIndex));
        if (!VEConstants.EncoderType.SUBTITLE.equals(streamInfo.getCodecType())) {
            throw new IllegalArgumentException("stream index " + streamIndex + " is " + streamInfo.getCodecType() + " not a subtitle stream");
        }

        // 根据字幕编码格式确定输出格式和Content-Type
        String format;
        String contentType;
        if (SUBTITLE_BIT_ENCODERS.contains(streamInfo.getCodecName())) {
            // 位图字幕格式（如SUP）
            format = VEConstants.SubtitleType.SUP;
            contentType = "application/x-sup";
        } else if (VEConstants.SubtitleType.ASS.equals(streamInfo.getCodecName()) || VEConstants.SubtitleType.SSA.equals(streamInfo.getCodecName())) {
            // ASS/SSA字幕格式
            format = VEConstants.SubtitleType.ASS;
            contentType = "text/x-ass";
        } else {
            // 默认转换为WEBVTT格式（Web标准字幕格式）
            format = VEConstants.SubtitleType.WEBVTT;
            contentType = "text/vtt";
        }

        Resource output;
        String cacheFileName;
        // 获取临时存储服务并确保字幕缓存目录存在
        TempStoreService tempStoreService = storeServiceFactory.getTempStoreService();
        if(!tempStoreService.exist("ve/subtitle")) {
            tempStoreService.mkdirs("ve/subtitle");
        }

        // 生成缓存文件名（基于文件特征或资源特征）
        if (resource.isFile()) {

            // 本地文件：使用文件路径、修改时间和大小生成MD5
            File file = resource.getFile();
            cacheFileName = SecureUtils.getMd5(file.getPath() + "_" + file.lastModified() + "_" + file.length()) + "." + format;
        } else {

            // 远程资源：使用路径、文件名、修改时间和内容长度生成MD5
            cacheFileName = SecureUtils.getMd5(
                    StringUtils.appendPath(resourceRequest.getPath(), resource.getFilename()) + "_"
                            + resource.lastModified() + "_"
                            + resource.contentLength()) + "."
                    + format;
        }
        String cachePath = "ve/subtitle/" + cacheFileName;
        if (!tempStoreService.exist(cachePath)) {
            Path tmpFilePath = PathUtils.getTempPath().resolve(cacheFileName);
            String tmpFile = tmpFilePath.toString();
            try {
                this.generateSubtitleFile(resource, tmpFile, streamIndex);
                try (InputStream inputStream = Files.newInputStream(tmpFilePath)) {
                    tempStoreService.store(FileInfo.getLocal(tmpFile), cachePath, Files.size(tmpFilePath), inputStream);
                }
            } finally {
                Files.deleteIfExists(tmpFilePath);
            }
        }
        output = tempStoreService.getResource(cachePath);
        return ResponseResource.create(output)
                .setResponseFilename(FileUtils.parseName(resourceRequest.getName())[0] + "." + format)
                .setContentType(contentType);
    }

    private void generateSubtitleFile(Resource videoResource, String savePath, String streamIndex) throws IOException {
        ProcessWrap wrap = videoService.extractStream(videoResource, savePath, streamIndex, null, "copy", VEConstants.EncoderType.SUBTITLE);
        String errMsg = wrap.waitProcess();
        if (errMsg != null) {
            log.error("FFmpeg调用出错: {}", errMsg);
            throw new RuntimeException("字幕获取失败");
        }
    }

    @Override
    public String getProtocolName() {
        return "subtitle";
    }
}
