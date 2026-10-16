package com.sfc.cloud.remote.client;

import com.sfc.cloud.remote.SfcRemoteApiException;
import com.sfc.cloud.model.json.SfcJsonResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * sfc-core 网盘文件「流式上传」客户端
 */
@Slf4j
class SfcCoreStreamFileClient {

    /** 上传接口路径 */
    private static final String UPLOAD_PATH = SfcCoreFileFeignClient.PREFIX + "/upload";

    /** 文件部件名 */
    private static final String PART_FILE = "file";

    /** 无法得知文件名时的兜底值（绝不能为空，否则 core 不把该部件视为文件） */
    private static final String DEFAULT_FILE_NAME = "upload.bin";

    /** 异常消息中的方法标识 */
    private static final String METHOD_KEY = "SfcCoreStreamFileClient#upload";

    /** 流式 RestClient（目标地址与凭证透传均由装配侧完成） */
    private final RestClient restClient;

    /**
     * 构造流式上传客户端（包内可见：仅由自动装配在构建 {@link SfcCoreFileClient} 时创建）。
     *
     * @param restClient 已装配好目标地址与凭证透传的流式 RestClient
     */
    SfcCoreStreamFileClient(RestClient restClient) {
        this.restClient = Objects.requireNonNull(restClient, "restClient 不能为 null");
    }

    /**
     * 以流式方式上传文件到 core。
     *
     * <p>文件内容从 {@code file} 边读边写，调用方需保证在上传期间该资源可读；上传完成后本方法不会关闭它。</p>
     *
     * @param file  文件资源，建议使用文件型资源（{@code FileSystemResource}，支持边读边发）；
     *              使用无文件名的资源（如 {@code InputStreamResource}、{@code ByteArrayResource}）时请显式传 {@code name}
     * @param uid   保存到的用户ID，0 表示公共网盘，写入公共网盘需要管理员权限
     * @param path  保存到的目录路径，不含文件名，根目录为 "/"
     * @param name  保存的文件名，为 null 时使用资源的文件名
     * @param mtime 文件修改时间（毫秒时间戳），为 null 时由服务端决定
     * @return core 的响应信封（含保存结果状态码）；失败时抛 {@link SfcRemoteApiException}
     */
    SfcJsonResult<Long> upload(Resource file, long uid, String path, String name, Long mtime) {
        Objects.requireNonNull(file, "file 不能为 null");
        Objects.requireNonNull(path, "path 不能为 null");

        String fileName = resolveFileName(file, name);
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>(5);
        parts.add(PART_FILE, new HttpEntity<>(file, filePartHeaders(fileName)));
        parts.add("uid", String.valueOf(uid));
        parts.add("path", path);
        if (name != null) {
            parts.add("name", name);
        }
        if (mtime != null) {
            parts.add("mtime", String.valueOf(mtime));
        }

        long start = System.currentTimeMillis();
        SfcJsonResult<Long> result = restClient.post()
                .uri(UPLOAD_PATH)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(parts)
                .retrieve()
                .body(new ParameterizedTypeReference<SfcJsonResult<Long>>() {
                });
        if (result == null) {
            throw new SfcRemoteApiException(200, 500, 200, METHOD_KEY, "core 上传接口返回空响应体");
        }
        log.debug("[sfc-remote] 流式上传完成：uid={}, path={}, name={}, cost={}ms",
                uid, path, fileName, System.currentTimeMillis() - start);
        return result;
    }

    /**
     * 解析文件部件写入的 filename。core 只把带 filename 的部件视为文件，因此这里保证永不返回空值。
     *
     * @param file 文件资源
     * @param name 显式文件名
     * @return 文件名（必非空）
     */
    private String resolveFileName(Resource file, String name) {
        if (StringUtils.hasText(name)) {
            return name;
        }
        String resourceName = file.getFilename();
        return (StringUtils.hasText(resourceName) ? resourceName : DEFAULT_FILE_NAME);
    }

    /**
     * 构建文件部件请求头。显式写入 Content-Disposition 后，Spring 的表单转换器不会再覆盖它。
     *
     * @param fileName 文件名
     * @return 部件请求头
     */
    private HttpHeaders filePartHeaders(String fileName) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.formData()
                .name(PART_FILE)
                .filename(fileName, StandardCharsets.UTF_8)
                .build());
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        return headers;
    }
}
