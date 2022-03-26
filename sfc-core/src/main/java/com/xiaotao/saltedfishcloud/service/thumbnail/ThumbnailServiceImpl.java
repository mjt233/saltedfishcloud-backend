package com.xiaotao.saltedfishcloud.service.thumbnail;

import com.xiaotao.saltedfishcloud.service.file.FileResourceMd5Resolver;
import com.xiaotao.saltedfishcloud.service.file.StoreServiceProvider;
import com.xiaotao.saltedfishcloud.service.file.TempStoreService;
import com.xiaotao.saltedfishcloud.service.file.thumbnail.ThumbnailHandler;
import com.xiaotao.saltedfishcloud.service.file.thumbnail.ThumbnailService;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
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
    private final Map<String, ThumbnailHandler> handlerCache = new ConcurrentHashMap<>();

    private final StoreServiceProvider storeServiceProvider;
    private final FileResourceMd5Resolver md5Resolver;

    @Autowired(required = false)
    @Lazy
    private List<ThumbnailHandler> handlerList;

    public String getThumbnailTempPath(String md5) {
        return "thumbnail/" + StringUtils.getUniquePath(md5);
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
     * @param md5   源文件md5
     * @param ext   源文件类型（拓展名）
     * @return      缩略图资源，生成失败则为null
     */
    protected Resource generate(String md5, String ext) throws IOException {

        try {
            final Resource originResource = md5Resolver.getResourceByMd5(md5);
            final TempStoreService tempHandler = storeServiceProvider.getService().getTempFileHandler();
            final String thumbnailPath = getThumbnailTempPath(md5);
            if (originResource == null) {
                return null;
            }
            try(
                    final InputStream input = originResource.getInputStream();
                    final OutputStream output = tempHandler.newOutputStream(thumbnailPath)
            ) {
                ThumbnailHandler handler = findHandler(ext);
                handler.generate(input, ext, output);
                if (log.isDebugEnabled()) {
                    log.debug("{}生成器：{} 类型：{} 生成缩略图保存到：{} ", LOG_TITLE, handler.getClass().getSimpleName(), ext, thumbnailPath);
                }
            } catch (Exception e) {
                tempHandler.delete(thumbnailPath);
                e.printStackTrace();
            }

            return tempHandler.getResource(thumbnailPath);
        } catch (NoSuchFileException e) {
            log.debug("{}md5文件资源不存在：{}", LOG_TITLE, md5);
            throw e;
        }
    }

    /**
     * 尝试获取已经生成过的缩略图资源
     * @param md5       源文件md5
     * @return          缩略图资源，若不存在则为null
     */
    protected Resource getFromExist(String md5) throws IOException {
        final String thumbnailPath = getThumbnailTempPath(md5);
        final TempStoreService tempHandler = storeServiceProvider.getService().getTempFileHandler();
        final Resource resource = tempHandler.getResource(thumbnailPath);
        if (resource != null) {
            log.debug("{}已有缩略图：{}", LOG_TITLE, md5);
            return resource;
        } else {
            return null;
        }
    }

    @Override
    public Resource getThumbnail(String md5, String ext) throws IOException {

        // 优先从已生成的资源中获取，若不存在再进行生成操作
        Resource resource = getFromExist(md5);
        if (resource != null) {
            return resource;
        }

        return generate(md5, ext);
    }

    @Override
    public void registerHandler(ThumbnailHandler thumbnailHandler) {
        String name = thumbnailHandler.getClass().getSimpleName();
        for (String s : thumbnailHandler.getSupportType()) {
            handlerCache.put(s, thumbnailHandler);
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
