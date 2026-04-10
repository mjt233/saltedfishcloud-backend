package com.xiaotao.saltedfishcloud.service.thumbnail;

import com.xiaotao.saltedfishcloud.model.config.SysCommonConfig;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.config.ConfigService;
import com.xiaotao.saltedfishcloud.service.file.FileResourceMd5Resolver;
import com.xiaotao.saltedfishcloud.service.file.StoreServiceFactory;
import com.xiaotao.saltedfishcloud.service.file.TempStoreService;
import com.xiaotao.saltedfishcloud.service.file.thumbnail.ThumbnailHandler;
import com.xiaotao.saltedfishcloud.service.file.thumbnail.ThumbnailService;
import com.xiaotao.saltedfishcloud.constant.ByteSize;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
@Service
@Slf4j
public class ThumbnailServiceImpl implements ThumbnailService, ApplicationRunner {
    private final static String LOG_TITLE = "[Thumbnail]";
    /**
     * key - 文件拓展名，value - 对应的缩略图生成器
     */
    private final Map<String, ThumbnailHandler> handlerCache = new ConcurrentHashMap<>();

    private final StoreServiceFactory storeServiceFactory;
    private final FileResourceMd5Resolver md5Resolver;
    private final RedissonClient redisson;

    @Autowired(required = false)
    @Lazy
    private List<ThumbnailHandler> handlerList;

    @Autowired
    @Lazy
    private ConfigService configService;

    /**
     * 最大可提取缩略图的源文件大小限制
     */
    private Double maxThumbnailResourceSize = 128D;

    /**
     * 是否停用缩略图缓存
     */
    private Boolean disableThumbnailCache = false;

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
     * 寻找合适的缩略图生成器
     * @param ext   缩略图源文件拓展名类型
     * @return      缩略图生成器
     */
    protected ThumbnailHandler findHandler(String ext) {
        ThumbnailHandler handler = handlerCache.get(ext);
        if (handler == null) {
            throw new NullPointerException("为" + ext + "类型找不到合适的生成器");
        }
        return handler;
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
        if (resource == null || resource.contentLength() == 0 || resource.contentLength() > ByteSize._1MiB * maxThumbnailResourceSize) {
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

        // 生成成功，如果缓存未禁用，则保存到临时文件
        String thumbnailPath = getThumbnailTempPath(id);
        if (!disableThumbnailCache) {
            log.debug("{}保存缩略图缓存 {}", LOG_TITLE, thumbnailPath);
            FileInfo tempFile = new FileInfo();
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
        if (disableThumbnailCache) {
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
    public void run(ApplicationArguments args) throws Exception {
        refreshRegister();
        configService.addAfterSetListener(SysCommonConfig::getMaxThumbnailResourceSize, val -> this.maxThumbnailResourceSize = val);
        configService.addAfterSetListener(SysCommonConfig::getDisableThumbnailCache, val -> this.disableThumbnailCache = val);
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
