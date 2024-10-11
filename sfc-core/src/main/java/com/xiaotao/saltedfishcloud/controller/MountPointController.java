package com.xiaotao.saltedfishcloud.controller;

import com.xiaotao.saltedfishcloud.constant.error.CommonError;
import com.xiaotao.saltedfishcloud.exception.FileSystemParameterException;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.model.param.MountPointSyncFileRecordParam;
import com.xiaotao.saltedfishcloud.model.po.MountPoint;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemDescribe;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemFactory;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.service.mountpoint.MountPointService;
import com.xiaotao.saltedfishcloud.validator.UIDValidator;
import com.xiaotao.saltedfishcloud.validator.annotations.UID;
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
    private DiskFileSystemManager fileSystemManager;

    /**
     * 获取可用的文件系统
     */
    @GetMapping("listAvailableFileSystem")
    public JsonResult<List<DiskFileSystemDescribe>> listAvailableFileSystem() {
        return JsonResultImpl.getInstance(fileSystemManager.listPublicFileSystem().stream().map(DiskFileSystemFactory::getDescribe).collect(Collectors.toList()));
    }

    /**
     * 添加/修改挂载点信息
     * @param mountPoint    挂载点信息
     */
    @PutMapping("setMountPoint")
    public JsonResult<?> setMountPoint(@RequestBody MountPoint mountPoint) throws IOException, FileSystemParameterException {
        mountPointService.saveMountPoint(mountPoint);
        return JsonResult.emptySuccess();
    }

    /**
     * 根据用户id查询所有的挂载点
     * @param uid 用户id
     */
    @GetMapping("listByUid")
    public JsonResult<List<MountPoint>> listByUid(@UID @RequestParam("uid") long uid) {
        return JsonResultImpl.getInstance(mountPointService.findByUid(uid));
    }

    /**
     * 根据挂载点id获取挂载点信息
     */
    @GetMapping("getById")
    public JsonResult<MountPoint> getById(@RequestParam("id") long id) {
        MountPoint mountPoint = mountPointService.findById(id);
        if (mountPoint != null) {
            if(!UIDValidator.validate(mountPoint.getUid(), true)) {
                throw new JsonException(CommonError.SYSTEM_FORBIDDEN);
            }
        } else {
            throw new JsonException(CommonError.RESOURCE_NOT_FOUND);
        }
        return JsonResultImpl.getInstance(mountPoint);
    }

    /**
     * 移除挂载点
     * @param uid   挂载点的用户id
     * @param id    挂载点id
     */
    @DeleteMapping("remove")
    public JsonResult<?> delete(@UID @RequestParam("uid") long uid, @RequestParam("id") long id) {
        mountPointService.remove(uid, id);
        return JsonResult.emptySuccess();
    }

    /**
     * 同步挂载点的文件信息到文件记录服务
     */
    @PostMapping("syncFileRecord")
    public JsonResult<?> syncFileRecord(@RequestBody MountPointSyncFileRecordParam param) throws FileSystemParameterException, IOException {
        mountPointService.syncFileRecord(param);
        return JsonResult.emptySuccess();
    }

}
