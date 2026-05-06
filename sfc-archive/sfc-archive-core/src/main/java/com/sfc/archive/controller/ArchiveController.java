package com.sfc.archive.controller;

import com.sfc.archive.ArchiveEngineDecompressor;
import com.sfc.archive.ArchiveEngineManager;
import com.sfc.archive.ArchiveEngineProvider;
import com.sfc.archive.model.ArchiveEngineProperty;
import com.sfc.archive.model.ArchiveResource;
import com.sfc.archive.model.AsyncArchiveExtractParam;
import com.sfc.archive.model.DiskFileSystemCompressParam;
import com.sfc.archive.model.ListArchiveResourcesRequest;
import com.sfc.archive.service.DiskFileSystemArchiveService;
import com.sfc.task.AsyncTaskManager;
import com.sfc.task.model.AsyncTaskRecord;
import com.xiaotao.saltedfishcloud.constant.AsyncTaskType;
import com.xiaotao.saltedfishcloud.enums.ArchiveError;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.exception.UnsupportedProtocolException;
import com.xiaotao.saltedfishcloud.model.dto.ResourceRequest;
import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.model.po.UserPrincipal;
import com.xiaotao.saltedfishcloud.service.resource.ResourceService;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.xiaotao.saltedfishcloud.validator.UIDValidator;
import com.xiaotao.saltedfishcloud.validator.ValidPathValidator;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/**
 * 压缩包操作控制器，提供文件在线解压等异步操作接口。
 */
@RestController
@RequestMapping("/api/archive")
@Validated
@RequiredArgsConstructor
@Api(tags = "压缩包操作")
public class ArchiveController {

    private final AsyncTaskManager asyncTaskManager;
    private final DiskFileSystemArchiveService archiveService;
    private final ArchiveEngineManager archiveEngineManager;
    private final ResourceService resourceService;


    /**
     * 异步方式创建压缩任务
     * @param param     压缩参数
     * @return          任务id
     */
    @PostMapping("asyncCompress")
    public JsonResult<Long> asyncCompress(@RequestBody DiskFileSystemCompressParam param) throws IOException {
        return JsonResultImpl.getInstance(archiveService.asyncCompress(param));
    }

    /**
     * 创建文件在线解压异步任务。
     * <p>
     * 通过 {@link AsyncArchiveExtractParam} 指定待解压文件来源（支持任意协议的资源请求）、
     * 解压参数（编码、密码、扩展名等）、解压引擎 ID 以及解压目标用户和目录。
     * 任务提交后立即返回任务 ID，客户端可通过任务 ID 查询执行进度与日志。
     * </p>
     *
     * @param param 解压任务参数，包含源文件资源请求、解压参数、目标 uid 与路径
     * @return 提交成功的异步任务 ID
     * @throws IOException 构建任务参数时发生 IO 异常
     */
    @PostMapping("asyncExtract")
    @ApiOperation("创建文件在线解压异步任务")
    public JsonResult<Long> asyncExtract(@RequestBody AsyncArchiveExtractParam param) throws IOException {
        // UID 安全校验：公共资源（uid=0）的写入仅允许管理员
        UIDValidator.validateWithException(param.getUid(), true);
        ValidPathValidator.valid(param.getPath());

        // 构建异步任务记录
        AsyncTaskRecord record = new AsyncTaskRecord();
        record.setName("解压文件: " + Optional.ofNullable(param.getSource())
                .map(ResourceRequest::getName)
                .orElse("未知文件")
                + " -> " + param.getPath());
        record.setTaskType(AsyncTaskType.ARCHIVE_EXTRACTOR);
        record.setCpuOverhead(20);
        record.setParams(MapperHolder.toJson(param));

        // 关联当前登录用户
        UserPrincipal curUser = SecureUtils.getSpringSecurityUser();
        record.setUid(Optional.ofNullable(curUser).map(UserPrincipal::getId).orElse(param.getUid()));

        // 提交任务
        asyncTaskManager.submitAsyncTask(record);
        return JsonResultImpl.getInstance(record.getId());
    }

    /**
     * 读取压缩包内所有文件列表。
     * <p>
     * 通过 {@link ListArchiveResourcesRequest} 指定解压缩引擎ID、待查看的压缩包资源和解压缩参数，
     * 返回压缩包内的所有资源列表。
     * </p>
     *
     * @param request 包含引擎ID、资源请求和引擎属性的请求对象
     * @return 压缩包内的所有资源列表
     * @throws IOException 读取压缩包或获取资源列表时발生 IO 异常
     */
    @PostMapping("listResources")
    @ApiOperation("读取压缩包内所有文件列表")
    public JsonResult<List<ArchiveResource>> listResources(@RequestBody ListArchiveResourcesRequest request) throws IOException, UnsupportedProtocolException {

        // 允许返回的压缩包资源列表最大条目数，超过后直接拒绝，防止内存占用过高。
        int maxArchiveResourceCount = 20000;

        // 获取并验证解压缩引擎
        ArchiveEngineProvider provider = archiveEngineManager.getEngineProvider(request.getEngineProviderId());

        // 获取资源请求和引擎属性
        ResourceRequest resourceRequest = request.getResourceRequest();
        ArchiveEngineProperty engineProperty = request.getEngineProperty();

        // 创建Resource对象（根据协议创建对应的Resource）
        Resource resource = resourceService.getResource(resourceRequest);

        // 非本地资源且引擎声明读取列表需本地文件时，拒绝执行，避免高 IO 开销。
        if (!isLocalFileResource(resource) && provider.requiresLocalResourceForList(resource, engineProperty)) {
            throw new JsonException(ArchiveError.ARCHIVE_LIST_RESOURCE_NOT_SUPPORTED);
        }

        // 创建解压器
        try (ArchiveEngineDecompressor decompressor = archiveEngineManager.createEngineDecompressor(
                request.getEngineProviderId(),
                resource,
                engineProperty)) {

            // 获取资源列表
            Iterator<ArchiveResource> iterator = decompressor.getArchiveResources();
            List<ArchiveResource> resourceList = new ArrayList<>();
            while (iterator.hasNext()) {
                if (resourceList.size() >= maxArchiveResourceCount) {
                    throw new JsonException(ArchiveError.ARCHIVE_LIST_RESOURCE_TOO_MANY,
                            "max=" + maxArchiveResourceCount);
                }
                resourceList.add(iterator.next());
            }

            return JsonResultImpl.getInstance(resourceList);
        }
    }

    /**
     * 判断资源是否位于本地文件系统。
     *
     * @param resource 待检查资源
     * @return 本地文件资源返回 true
     */
    private boolean isLocalFileResource(Resource resource) {
        return resource instanceof PathResource || resource.isFile();
    }
}

