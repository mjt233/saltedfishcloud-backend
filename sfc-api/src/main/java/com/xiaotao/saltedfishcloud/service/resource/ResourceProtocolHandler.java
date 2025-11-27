package com.xiaotao.saltedfishcloud.service.resource;

import com.xiaotao.saltedfishcloud.helper.OutputStreamConsumer;
import com.xiaotao.saltedfishcloud.model.PermissionInfo;
import com.xiaotao.saltedfishcloud.model.dto.ResourceRequest;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.identifier.IdUtil;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;


/**
 * 资源协议操作器，用于统一各个文件资源的操作。
 */
public interface ResourceProtocolHandler {

    /**
     * 获取请求资源的权限信息
     * @param param 资源请求参数
     * @return      权限信息
     */
    PermissionInfo getPermissionInfo(ResourceRequest param);

    /**
     * 根据参数获取文件资源
     * @param param     资源请求参数
     * @return          文件资源，若无法获取则返回null，支持资源重定向
     */
    Resource getFileResource(ResourceRequest param) throws IOException;

    /**
     * 获取请求的资源最终映射在咸鱼云网盘上的路径标识，该接口方法用于让外部调用者能够识别同一份文件在不同访问方式上的唯一路径标识，用于跟踪和更新与该文件相关的变更<br>
     * 例如：用户A分享目录下的
     * <pre>/设计资料/产品样例.psd</pre>
     * 与用户A私人网盘下的
     * <pre>/项目归档/某项目/设计资料/产品样例.psd</pre>
     * 是相同的文件路径引用，那么在文件分享资源操作器 和 主存储资源操作器上 该资源的getMappingIdentity方法返回值应该是相等的。<br>
     *
     * 为了防止在外部系统中直接暴露真实引用路径，该方法应采用以下方式对路径标识进行编码。
     * <pre><code>
     *     md5(资源所有者标识:文件的真实完整路径)
     * </code></pre>
     * 案例：
     * <pre><code>
     *     md5(114514:/项目归档/某项目/设计资料/产品样例.psd)
     * </code></pre>
     *
     * <br>
     * 例外情况: 如果请求的资源路径并不是与咸鱼云网盘的主资源存储直接映射的，或者是一个抽象的资源，则不必绝对遵循上述的编码规则。<br>
     * 若在实际实现中如果与其他资源协议存储存在某种关联，则建议在编码过程中调用对应资源协议操作器的getMappingIdentity作为编码实现的过程参数，作为同一个资源引用之下的抽象资源的路径唯一标识。
     *
     * @param param 资源请求参数
     * @return  唯一资源引用标识。若资源不存在则返回null。
     */
    String getPathMappingIdentity(ResourceRequest param);

    /**
     * 获取支持的资源协议名
     */
    String getProtocolName();

    /**
     * 是否支持写入资源
     */
    default boolean isWriteable() {
        return false;
    }

    /**
     * 执行资源写入
     * @param param     资源定位请求参数
     * @param resource  待写入资源
     */
    default void writeResource(ResourceRequest param, Resource resource) throws IOException {
        throw new UnsupportedOperationException();
    }


    /**
     * 通过输出流的方式将文件写入到指定的资源中
     * @param param 资源定位请求参数
     * @param outputStream 待写入数据的输出流
     */
    default void writeResource(ResourceRequest param, OutputStreamConsumer<OutputStream> outputStream) throws IOException {
        if(!isWriteable()) {
            throw new UnsupportedOperationException();
        }
        Path tmpFile = PathUtils.getTempPath().resolve(IdUtil.getUUID());
        try(OutputStream os = Files.newOutputStream(tmpFile)) {
            outputStream.accept(os);
            os.close();
            writeResource(param, new PathResource(tmpFile));
        } finally {
            Files.deleteIfExists(tmpFile);
        }
    }

}
