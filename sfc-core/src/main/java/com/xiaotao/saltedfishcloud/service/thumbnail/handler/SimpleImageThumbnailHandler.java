package com.xiaotao.saltedfishcloud.service.thumbnail.handler;

import com.xiaotao.saltedfishcloud.service.file.thumbnail.ThumbnailHandler;
import com.xiaotao.saltedfishcloud.utils.ImageUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 简单的位图缩略图生成器
 */
@Component
public class SimpleImageThumbnailHandler implements ThumbnailHandler {
    private final static List<String> supportList = Collections.unmodifiableList(new ArrayList<String>(){{
        add("jpg");
        add("jpeg");
        add("png");
        add("gif");
        add("bmp");
    }});

    @Override
    public void generate(InputStream inputStream, String type, OutputStream outputStream) throws IOException {
        ImageUtils.generateThumbnail(inputStream, 300, outputStream);
    }

    @Override
    public List<String> getSupportType() {
        return supportList;
    }
}
