package com.xiaotao.saltedfishcloud.service.file.thumbnail;


import com.xiaotao.saltedfishcloud.service.hello.FeatureProvider;
import com.xiaotao.saltedfishcloud.service.hello.HelloService;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * 缩略图操作器，为生成缩略图提供能力支持
 */
public interface ThumbnailHandler extends FeatureProvider {

    /**
     * 从流中读取数据数据生成缩略图
     * @param resource      原图资源
     * @param type          文件类型（文件拓展名，不带.前缀）
     * @param outputStream  缩略图输出流，内部不close该流
     * @return 是否生成成功
     */
    boolean generate(Resource resource, String type, OutputStream outputStream) throws IOException;

    /**
     * 获取支持的缩略图类型
     * @return  支持的缩略图类型（即文件拓展名，不带.前缀）
     */
    List<String> getSupportType();

    /**
     * 获取该类型缩略图生成器的人类阅读友好名称<br>
     * 注意：需确保该值为系统全局唯一
     */
    String getName();


    default void registerFeature(HelloService helloService) {
        for (String s : getSupportType()) {
            helloService.appendFeatureDetail("thumbType", s);
        }
    }
}
