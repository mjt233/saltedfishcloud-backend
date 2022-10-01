package com.xiaotao.saltedfishcloud.controller;

import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.model.po.MountPoint;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemFactory;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.service.mountpoint.MountPointService;
import com.xiaotao.saltedfishcloud.validator.annotations.UID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

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
    public JsonResult listAvailableFileSystem() {
        return JsonResultImpl.getInstance(fileSystemManager.listPublicFileSystem().stream().map(DiskFileSystemFactory::getDescribe).collect(Collectors.toList()));
    }

    /**
     * 添加/修改挂载点信息
     * @param mountPoint    挂载点信息
     */
    @PutMapping("setMountPoint")
    public JsonResult setMountPoint(@RequestBody MountPoint mountPoint) {
        mountPointService.saveMountPoint(mountPoint);
        return JsonResult.emptySuccess();
    }

    /**
     * 根据用户id查询所有的挂载点
     * @param uid 用户id
     */
    @GetMapping("listByUid")
    public JsonResult listByUid(@UID @RequestParam("uid") long uid) {
        return JsonResultImpl.getInstance(mountPointService.findByUid(uid));
    }

    /**
     * 移除挂载点
     * @param uid   挂载点的用户id
     * @param id    挂载点id
     */
    @DeleteMapping("remove")
    public JsonResult delete(@UID @RequestParam("uid") long uid, @RequestParam("id") long id) {
        mountPointService.remove(uid, id);
        return JsonResult.emptySuccess();
    }


}
