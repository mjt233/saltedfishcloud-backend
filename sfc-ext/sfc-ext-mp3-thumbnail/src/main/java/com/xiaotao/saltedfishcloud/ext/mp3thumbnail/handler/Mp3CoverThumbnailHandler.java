package com.xiaotao.saltedfishcloud.ext.mp3thumbnail.handler;

import com.mpatric.mp3agic.InvalidDataException;
import com.mpatric.mp3agic.Mp3File;
import com.mpatric.mp3agic.UnsupportedTagException;
import com.xiaotao.saltedfishcloud.config.SysProperties;
import com.xiaotao.saltedfishcloud.service.file.thumbnail.ThumbnailHandler;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import com.xiaotao.saltedfishcloud.utils.ImageUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 为mp3音频文件创建封面缩略图
 */
@Slf4j
public class Mp3CoverThumbnailHandler implements ThumbnailHandler {
    private final static String LOG_TITLE = "[AudioCoverThumbnailHandler]";
    private final static List<String> SUPPORT_LIST = Collections.unmodifiableList(new ArrayList<String>(){{
        add("mp3");
    }});

    @Autowired
    private SysProperties sysProperties;

    @Override
    public boolean generate(Resource resource, String type, OutputStream outputStream) throws IOException {
        File file;
        boolean isTempFile = false;

        // 若资源不可直接获取为File，则把流保存到本地作为临时文件
        try {
            file = resource.getFile();
        } catch (FileNotFoundException e) {
            log.debug("{}从流中保存资源到本地为临时文件", LOG_TITLE);
            file = FileUtils.saveStreamAsLocalTempFile(resource, sysProperties.getStore().getLocalTempDir());
            log.debug("{}临时文件已保存，路径：{}", LOG_TITLE, file.getAbsolutePath());
            isTempFile = true;
        }

        // 读取文件提取封面
        try {
            Mp3File mp3File = new Mp3File(file);
            byte[] albumImage = mp3File.getId3v2Tag().getAlbumImage();
            try(ByteArrayInputStream stream = new ByteArrayInputStream(albumImage)) {
                ImageUtils.generateThumbnail(stream, 1200, outputStream);
            }
        } catch (UnsupportedTagException | InvalidDataException e) {
            e.printStackTrace();
            return false;
        } finally {
            // 清理临时文件
            if (isTempFile && file.exists()) {
                log.debug("{}清理临时文件{}，结果：{}", LOG_TITLE, file.getAbsolutePath(), file.delete());
            }
        }
        return true;
    }

    @Override
    public List<String> getSupportType() {
        return SUPPORT_LIST;
    }
}
