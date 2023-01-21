package com.xiaotao.saltedfishcloud.ext;

import com.xiaotao.saltedfishcloud.exception.PluginNotFoundException;
import com.xiaotao.saltedfishcloud.model.PluginInfo;
import com.xiaotao.saltedfishcloud.model.vo.PluginInfoVo;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.List;

/**
 * 插件服务接口
 */
public interface PluginService {

    /**
     * 获取注册的插件列表
     */
    List<PluginInfo> listPlugins();

    /**
     * 获取所有可被识别的插件列表（包括未加载、已加载和待删除的）
     */
    List<PluginInfo> listAvailablePlugins() throws IOException;

    /**
     * 临时保存一个插件并生成插件的临时id和解析插件信息
     * @param resource  插件资源
     */
    PluginInfoVo uploadPlugin(Resource resource) throws IOException;

    /**
     * 安装一个临时保存的插件
     * @param tempId 插件临时id
     * @param fileName 插件原始文件名
     */
    void installPlugin(Long tempId, String fileName) throws IOException;

    /**
     * 删除一个插件
     */
    void deletePlugin(String name) throws IOException;

    /**
     * 获取插件的静态文件资源
     * @param name  插件名称
     * @param path  静态资源路径（相对于的插件静态资源目录的路径）
     */
    Resource getPluginStaticResource(String name, String path) throws PluginNotFoundException;

    /**
     * 获取所有插件的自动加载资源的合并资源
     * @param type  资源类型（后缀名）
     */
    Resource getMergeAutoLoadResource(String type);
}
