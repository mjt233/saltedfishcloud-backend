package com.xiaotao.saltedfishcloud.service.resource;

import com.xiaotao.saltedfishcloud.helper.OutputStreamConsumer;
import com.xiaotao.saltedfishcloud.model.PermissionInfo;
import com.xiaotao.saltedfishcloud.model.dto.ResourceRequest;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;

/**
 *
 * @param <T> 从原始资源请求中解析出的特定的ProtocolHandler的参数
 */
public abstract class AbstractResourceProtocolHandler<T> implements ResourceProtocolHandler, ApplicationRunner {
    @Autowired
    protected ResourceService resourceService;

    /**
     * 校验并解析原始请求参数。不通过校验时应抛出异常
     * @param resourceRequest 原始请求参数
     * @return  校验通过，返回解析后的参数
     */
    public abstract T validAndParseParam(ResourceRequest resourceRequest);

    /**
     * 获取请求资源的权限信息
     * @param resourceRequest 原始资源请求参数
     * @param param   验证并二次解析加工后的资源请求参数
     */
    public abstract PermissionInfo getPermissionInfo(ResourceRequest resourceRequest, T param);

    @Override
    public PermissionInfo getPermissionInfo(ResourceRequest resourceRequest) {
        return getPermissionInfo(resourceRequest, validAndParseParam(resourceRequest));
    }

    /**
     * 根据参数获取文件资源
     * @param resourceRequest 原始资源请求参数
     * @param param   验证并二次解析加工后的资源请求参数
     */
    public abstract Resource getFileResource(ResourceRequest resourceRequest, T param) throws IOException;

    @Override
    public Resource getFileResource(ResourceRequest resourceRequest) throws IOException {
        return getFileResource(resourceRequest, validAndParseParam(resourceRequest));
    }

    /**
     * 同 {@link ResourceProtocolHandler#getPathMappingIdentity(ResourceRequest)}，只是多了个parsedParam参数
     * @param resourceRequest 原始资源请求参数
     * @param param   验证并二次解析加工后的资源请求参数
     */
    public abstract String getPathMappingIdentity(ResourceRequest resourceRequest, T param);

    @Override
    public String getPathMappingIdentity(ResourceRequest resourceRequest) {
        return getPathMappingIdentity(resourceRequest, validAndParseParam(resourceRequest));
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        resourceService.addResourceHandler(this);
    }

    /**
     * 根据请求信息创建一个文件信息
     * @param resourceRequest 请求参数
     * @param resource  文件资源
     */
    protected FileInfo createFileInfoFromRequest(ResourceRequest resourceRequest,@Nullable Resource resource) throws IOException {
        FileInfo fileInfo = new FileInfo();
        Date now = new Date();
        long uid = Long.parseLong(resourceRequest.getTargetId());
        fileInfo.setCtime(now.getTime());
        if (resource != null) {
            fileInfo.setStreamSource(resource);
            fileInfo.setSize(resource.contentLength());
            fileInfo.setMtime(resource.lastModified());
        }


        fileInfo.setUid(uid);
        fileInfo.setName(resourceRequest.getName());

        if (resourceRequest.getMtime() != null) {
            fileInfo.setMtime(resourceRequest.getMtime());
        }
        String md5 = resourceRequest.getMd5();
        if (md5 != null) {
            fileInfo.setMd5(md5);
        }
        return fileInfo;
    }

    @Override
    public void writeResource(ResourceRequest param, OutputStreamConsumer<OutputStream> streamConsumer) throws IOException {
        throw new UnsupportedOperationException();
    }
}
