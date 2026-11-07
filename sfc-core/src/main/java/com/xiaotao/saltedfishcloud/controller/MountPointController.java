package com.xiaotao.saltedfishcloud.controller;

import com.sfc.common.constant.SfcCommonError;
import com.xiaotao.saltedfishcloud.exception.FileSystemParameterException;
import com.sfc.common.exception.SfcJsonException;
import com.sfc.common.model.json.SfcJsonResult;
import com.xiaotao.saltedfishcloud.model.param.MountPointSyncFileRecordParam;
import com.xiaotao.saltedfishcloud.model.po.MountPoint;
import com.xiaotao.saltedfishcloud.service.file.StorageMetadata;
import com.xiaotao.saltedfishcloud.service.file.StorageFactory;
import com.xiaotao.saltedfishcloud.service.file.StorageRegistry;
import com.xiaotao.saltedfishcloud.service.mountpoint.MountPointService;
import com.sfc.cloud.data.validation.SfcUIDValidator;
import com.sfc.cloud.data.validation.SfcUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping(MountPointController.PREFIX)
@Validated
public class MountPointController {
    public static final String PREFIX = "/api/mountPoint";

    @Autowired
    private MountPointService mountPointService;

    @Autowired
    private StorageRegistry storageRegistry;

    /**
     * 获取可用的外部存储
     */
    @GetMapping({
            "listAvailableFileSystem",
            "listAvailableStorage"
    })
    public SfcJsonResult<List<StorageMetadata>> listAvailableFileSystem() {
        return SfcJsonResult.getInstance(storageRegistry.listPublicStorageFactory().stream().map(StorageFactory::getMetadata).collect(Collectors.toList()));
    }

    /**
     * 添加/修改挂载点信息
     * @param mountPoint    挂载点信息
     */
    @PutMapping("setMountPoint")
    public SfcJsonResult<?> setMountPoint(@RequestBody MountPoint mountPoint) throws IOException, FileSystemParameterException {
        mountPointService.saveMountPoint(mountPoint);
        return SfcJsonResult.emptySuccess();
    }

    /**
     * 根据用户id查询所有的挂载点
     * @param uid 用户id
     */
    @GetMapping("listByUid")
    public SfcJsonResult<List<MountPoint>> listByUid(@SfcUID @RequestParam("uid") long uid) {
        return SfcJsonResult.getInstance(mountPointService.findByUid(uid));
    }

    /**
     * 根据挂载点id获取挂载点信息
     */
    @GetMapping("getById")
    public SfcJsonResult<MountPoint> getById(@RequestParam("id") long id) {
        MountPoint mountPoint = mountPointService.findById(id);
        if (mountPoint != null) {
            if(!SfcUIDValidator.validate(mountPoint.getUid(), true)) {
                throw new SfcJsonException(SfcCommonError.SYSTEM_FORBIDDEN);
            }
        } else {
            throw new SfcJsonException(SfcCommonError.RESOURCE_NOT_FOUND);
        }
        return SfcJsonResult.getInstance(mountPoint);
    }

    /**
     * 移除挂载点
     * @param uid   挂载点的用户id
     * @param id    挂载点id
     */
    @DeleteMapping("remove")
    public SfcJsonResult<?> delete(@SfcUID @RequestParam("uid") long uid, @RequestParam("id") long id) {
        mountPointService.remove(uid, id);
        return SfcJsonResult.emptySuccess();
    }

    /**
     * 同步挂载点的文件信息到文件记录服务
     */
    @PostMapping("syncFileRecord")
    public SfcJsonResult<?> syncFileRecord(@RequestBody MountPointSyncFileRecordParam param) throws FileSystemParameterException, IOException {
        mountPointService.syncFileRecord(param);
        return SfcJsonResult.emptySuccess();
    }

}
