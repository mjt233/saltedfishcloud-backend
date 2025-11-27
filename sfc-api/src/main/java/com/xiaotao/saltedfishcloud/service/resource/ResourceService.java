package com.xiaotao.saltedfishcloud.service.resource;

import com.xiaotao.saltedfishcloud.exception.UnsupportedProtocolException;
import com.xiaotao.saltedfishcloud.helper.OutputStreamConsumer;
import com.xiaotao.saltedfishcloud.model.dto.ResourceRequest;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.io.OutputStream;
import java.util.function.Consumer;

/**
 * 系统资源服务
 */
public interface ResourceService {
    /**
     * 添加一个资源操作器
     */
    void addResourceHandler(ResourceProtocolHandler handler);

    /**
     * 根据协议名称获取已注册的资源操作器
     * @param protocol  协议
     * @return          对应的资源操作器，若没有则为null
     */
    ResourceProtocolHandler getResourceHandler(String protocol);

    /**
     * 根据统一资源请求参数获取资源对象
     * @param param 资源请求参数
     * @return      对应的资源。若无法获取则返回null
     */
    Resource getResource(ResourceRequest param) throws UnsupportedProtocolException, IOException;

    /**
     * 根据文件下载码获取资源
     * @param dc    文件下载码
     * @param directDownload    是否直接下载
     * @return 文件资源
     */
    ResponseEntity<Resource> getResourceByDownloadCode(String dc, boolean directDownload) throws IOException;

    /**
     * 生成文件资源下载码
     * @param uid       用户id
     * @param path      文件所在路径
     * @param fileInfo  文件信息
     * @param expr      过期日期
     * @return          生成的文件下载码
     */
    String getFileDownloadCode(long uid, String path, FileInfo fileInfo, int expr) throws IOException;

    /**
     * 向目标资源操作器写入资源
     * @param param     资源定位参数
     * @param resource  待写入资源
     */
    void writeResource(ResourceRequest param, Resource resource) throws IOException;

    /**
     * 通过输出流的方式将文件写入到指定的资源中
     * @param param 资源定位请求参数
     * @param outputStream 待写入数据的输出流
     */
    void writeResource(ResourceRequest param, OutputStreamConsumer<OutputStream> outputStream) throws IOException;
}
