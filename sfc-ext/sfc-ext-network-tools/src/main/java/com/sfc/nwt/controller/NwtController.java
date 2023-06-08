package com.sfc.nwt.controller;

import com.sfc.nwt.model.NetworkInterfaceInfo;
import com.sfc.nwt.model.po.WolDevice;
import com.sfc.nwt.service.WolDeviceService;
import com.sfc.nwt.utils.NetworkUtils;
import com.xiaotao.saltedfishcloud.annotations.AllowAnonymous;
import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.validator.annotations.UID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.annotation.security.RolesAllowed;
import java.io.IOException;
import java.net.SocketException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 网络工具控制器
 */
@RequestMapping("/api/nwt")
@RestController
public class NwtController {
    @Autowired
    private WolDeviceService wolDeviceService;

    /**
     * 获取用户配置的WOL设备列表
     * @param uid   用户id
     * @return      WOL设备列表
     */
    @GetMapping("findByUid")
    @AllowAnonymous
    public JsonResult<List<WolDevice>> findByUid(@RequestParam("uid") @UID Long uid) {
        return JsonResultImpl.getInstance(wolDeviceService.findByUid(uid));
    }

    @PostMapping("saveWolDevice")
    public JsonResult<?> saveWolDevice(@RequestBody WolDevice wolDevice) {
        wolDeviceService.save(wolDevice);
        return JsonResult.emptySuccess();
    }

    /**
     * 唤醒设备
     * @param id    WOL设备id
     */
    @GetMapping("wakeWolDevice")
    public JsonResult<?> wakeWolDevice(Long id) throws IOException {
        wolDeviceService.wake(id);
        return JsonResult.emptySuccess();
    }

    @GetMapping("getAllInterface")
    @RolesAllowed({"ADMIN"})
    public JsonResult<List<NetworkInterfaceInfo>> getAllInterface() throws SocketException {
        return JsonResultImpl.getInstance(
                NetworkUtils.getAllConnectedInterface()
                        .stream()
                        .map(NetworkInterfaceInfo::of)
                        .collect(Collectors.toList())
        );
    }


}
