package com.xiaotao.saltedfishcloud.service.thumbnail;

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
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.NoSuchFileException;
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
    protected Resource generate(Resource resource, String ext, String id) throws IOException {
        ThumbnailHandler handler = handlerCache.get(ext.toLowerCase());
        if (handler == null) {
            return null;
        }

        try {
            final TempStoreService tempHandler = storeServiceFactory.getService().getTempFileHandler();

            final String thumbnailPath = getThumbnailTempPath(id);
            if (resource == null || resource.contentLength() == 0 || resource.contentLength() > ByteSize._1MiB * 128) {
                return null;
            }
            try(final OutputStream output = tempHandler.newOutputStream(thumbnailPath)) {
                boolean res = handler.generate(resource, ext, output);
                if (log.isDebugEnabled()) {
                    if (res) {
                        log.debug("{}生成成功 生成器：{} 类型：{} 生成缩略图保存到：{} ", LOG_TITLE, handler.getClass().getSimpleName(), ext, thumbnailPath);
                    } else {
                        log.debug("{}生成失败 生成器：{} 类型：{} 原保存路径：{} ", LOG_TITLE, handler.getClass().getSimpleName(), ext, thumbnailPath);
                    }
                }
            } catch (Exception e) {
                tempHandler.delete(thumbnailPath);
                log.error("缩略图生成异常, id:{} ",id, e);
            }

            return tempHandler.getResource(thumbnailPath);
        } catch (NoSuchFileException e) {
            log.debug("{}文件资源不存在：{}", LOG_TITLE, id);
            throw e;
        }
    }

    /**
     * 尝试获取已经生成过的缩略图资源
     * @param fileIdentify       源文件唯一标识
     * @return          缩略图资源，若不存在则为null
     */
    protected Resource getFromCache(String fileIdentify) throws IOException {
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
            return generate(resource, ext, fileIdentify);
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
