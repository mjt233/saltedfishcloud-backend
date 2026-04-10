package com.xiaotao.saltedfishcloud.service.thumbnail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.xiaotao.saltedfishcloud.model.NameValueType;
import com.xiaotao.saltedfishcloud.model.config.SysCommonConfig;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.config.ConfigService;
import com.xiaotao.saltedfishcloud.service.file.FileResourceMd5Resolver;
import com.xiaotao.saltedfishcloud.service.file.StoreServiceFactory;
import com.xiaotao.saltedfishcloud.service.file.TempStoreService;
import com.xiaotao.saltedfishcloud.service.file.thumbnail.ThumbnailHandler;
import com.xiaotao.saltedfishcloud.service.file.thumbnail.ThumbnailService;
import com.xiaotao.saltedfishcloud.constant.ByteSize;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import com.xiaotao.saltedfishcloud.utils.TypeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
@Service
@Slf4j
public class ThumbnailServiceImpl implements ThumbnailService, ApplicationRunner {
    private final static String LOG_TITLE = "[Thumbnail]";
    private final static long DEFAULT_SOURCE_FILE_MAX_SIZE_LIMIT = 32L;
    /**
     * key - 文件拓展名，value - 对应的缩略图生成器
     */
    private final Map<String, ThumbnailHandler> handlerCache = new ConcurrentHashMap<>();

    private final StoreServiceFactory storeServiceFactory;
    private final FileResourceMd5Resolver md5Resolver;
    private final RedissonClient redisson;
    private final SysCommonConfig sysCommonConfig;

    @Autowired(required = false)
    @Lazy
    private List<ThumbnailHandler> handlerList;

    @Autowired
    @Lazy
    private ConfigService configService;

    private final Map<String, Long> sourceFileMaxSizeCache = new ConcurrentHashMap<>();

    /**
     * 获取主存储服务中的缩略图文件缓存路径
     *
     * @param id 文件缩略图的唯一标识
     */
    public String getThumbnailTempPath(String id) {
        if (id.length() == 32) {
            return "thumbnail/md5/" + StringUtils.getUniquePath(id);
        } else if (id.length() > 4) {
            return "thumbnail/other/" + StringUtils.getUniquePath(id);
        } else {
            return "thumbnail/id/" + id;
        }

    }

    /**
     * 生成缩略图
     * @param resource 待生成缩略图的源资源
     * @param ext   源文件类型（拓展名）
     * @param id    缩略图唯一id
     * @return      缩略图资源，生成失败则为null
     */
    protected Resource doGenerate(Resource resource, String ext, String id) throws IOException {
        ThumbnailHandler handler = handlerCache.get(ext.toLowerCase());
        if (handler == null) {
            return null;
        }

        final TempStoreService tempHandler = storeServiceFactory.getService().getTempFileHandler();
        if (resource == null || resource.contentLength() == 0 || checkSourceFileMaxSizeLimit(resource.contentLength(), handler)) {
            return null;
        }

        // 先生成到内存中
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        boolean generateSuccess;
        try {
            generateSuccess = handler.generate(resource, ext, baos);
        } catch (Exception e) {
            log.error("{}缩略图生成异常, id:{} ", LOG_TITLE, id, e);
            baos.close();
            return null;
        }
        if (!generateSuccess) {
            baos.close();
            return null;
        }

        // 生成成功，如果缓存没有显式禁用，则保存到临时文件
        String thumbnailPath = getThumbnailTempPath(id);
        if (!Boolean.TRUE.equals(sysCommonConfig.getDisableThumbnailCache())) {
            log.debug("{}保存缩略图缓存 {}", LOG_TITLE, thumbnailPath);
            FileInfo tempFile = new FileInfo();
            tempFile.setName(PathUtils.getLastNode(thumbnailPath));
            tempFile.setSize((long)baos.size());
            tempFile.setPath(thumbnailPath);
            try(InputStream is = new ByteArrayInputStream(baos.toByteArray())) {
                tempHandler.store(tempFile, thumbnailPath, baos.size(), is);
            }
        }

        return new InputStreamResource(() -> new ByteArrayInputStream(baos.toByteArray()));
    }

    /**
     * 尝试获取已经生成过的缩略图资源
     * @param fileIdentify       源文件唯一标识
     * @return          缩略图资源，若不存在则为null
     */
    protected Resource getFromCache(String fileIdentify) throws IOException {
        if (Boolean.TRUE.equals(sysCommonConfig.getDisableThumbnailCache())) {
            return null;
        }
        final String thumbnailPath = getThumbnailTempPath(fileIdentify);
        final TempStoreService tempHandler = storeServiceFactory.getService().getTempFileHandler();
        final Resource resource = tempHandler.getResource(thumbnailPath);
        if (resource != null) {
            log.debug("{}已有缩略图：{}", LOG_TITLE, fileIdentify);
            return resource;
        } else {
            return null;
        }
    }

    @Override
    public Resource getThumbnail(Resource resource, String ext, String fileIdentify) throws IOException {
        // 优先从已生成的资源中获取，若不存在再进行生成操作
        Resource existResource = getFromCache(fileIdentify);
        if (existResource != null) {
            return existResource;
        }
        RLock lock = redisson.getLock(fileIdentify);
        try {
            lock.lock();

            existResource = getFromCache(fileIdentify);
            if (existResource != null) {
                return existResource;
            }
            return doGenerate(resource, ext, fileIdentify);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean isSupport(String ext) {
        return handlerCache.containsKey(ext);
    }

    @Override
    public Resource getThumbnail(String md5, String ext) throws IOException {
        Resource originResource = md5Resolver.getResourceByMd5(md5);
        return getThumbnail(originResource, ext, md5);
    }

    @Override
    public void registerHandler(ThumbnailHandler thumbnailHandler) {
        String name = thumbnailHandler.getClass().getSimpleName();
        for (String s : thumbnailHandler.getSupportType()) {
            handlerCache.put(s.toLowerCase(), thumbnailHandler);
        }
        log.info("{}为{}类型注册缩略图生成器{}", LOG_TITLE, thumbnailHandler.getSupportType(), name);
    }

    @Override
    public List<ThumbnailHandler> getRegisteredHandler() {
        return new ArrayList<>(handlerCache.values());
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        refreshRegister();
        configService.addAfterSetListener(SysCommonConfig::getMaxThumbnailResourceSizeConfig, this::updateMaxSourceFileSizeConfigCache);
        updateMaxSourceFileSizeConfigCache(sysCommonConfig.getMaxThumbnailResourceSizeConfig());
    }

    private void updateMaxSourceFileSizeConfigCache(String configJson) {
        try {
            if (!StringUtils.hasText(configJson)) {
                return;
            }
            MapperHolder.parseJsonToList(configJson, NameValueType.class).forEach(e -> sourceFileMaxSizeCache.put(
                    e.getName(),
                    Optional.ofNullable(TypeUtils.toLong(e.getValue())).orElse(DEFAULT_SOURCE_FILE_MAX_SIZE_LIMIT)
            ));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 检查文件大小是否超出了该缩略图可生成缩略图的源文件大小的限制
     * @param sourceFileSize    源文件大小
     * @param thumbnailHandler  缩略图生成器
     * @return  超出限制返回true，否则返回false
     */
    private boolean checkSourceFileMaxSizeLimit(long sourceFileSize, ThumbnailHandler thumbnailHandler) {
        long maxSize = Optional.ofNullable(sourceFileMaxSizeCache.get(thumbnailHandler.getName())).orElse(DEFAULT_SOURCE_FILE_MAX_SIZE_LIMIT);
        if (maxSize < 0) {
            return false;
        }
        return sourceFileSize > maxSize * ByteSize._1MiB;
    }

    @Override
    public void refreshRegister() {
        if (handlerList != null) {
            for (ThumbnailHandler handler : handlerList) {
                registerHandler(handler);
            }
        }
    }
}
