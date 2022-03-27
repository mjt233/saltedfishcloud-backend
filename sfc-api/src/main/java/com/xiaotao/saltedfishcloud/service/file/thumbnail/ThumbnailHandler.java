package com.xiaotao.saltedfishcloud.service.file.thumbnail;


import com.xiaotao.saltedfishcloud.service.hello.FeatureProvider;
import com.xiaotao.saltedfishcloud.service.hello.HelloService;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * 缩略图操作器，为生成缩略图提供能力支持
 */
public interface ThumbnailHandler extends FeatureProvider {

    /**
     * 从流中读取数据数据生成缩略图
     * @param resource      原图资源
     * @param type          文件类型
     * @param outputStream  缩略图输出流
     * @return 是否生成成功
     */
    boolean generate(Resource resource, String type, OutputStream outputStream) throws IOException;

    /**
     * 获取支持的缩略图类型
     * @return  支持的缩略图类型
     */
    List<String> getSupportType();


    default void registerFeature(HelloService helloService) {
        for (String s : getSupportType()) {
            helloService.appendFeatureDetail("thumbType", s);
        }
    }
}
