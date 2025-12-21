package com.sfc.nwt.controller;

import com.sfc.nwt.model.NetworkInterfaceInfo;
import com.sfc.nwt.model.po.WolDevice;
import com.sfc.nwt.service.WolDeviceService;
import com.sfc.nwt.upnp.UpnpDevicesManager;
import com.sfc.nwt.upnp.model.UpnpDevice;
import com.sfc.nwt.upnp.model.xml.UpnpDescribe;
import com.sfc.nwt.utils.NetworkUtils;
import com.xiaotao.saltedfishcloud.annotations.AllowAnonymous;
import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import com.xiaotao.saltedfishcloud.utils.URLUtils;
import com.xiaotao.saltedfishcloud.validator.annotations.UID;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.util.UrlUtils;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.security.RolesAllowed;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.net.http.HttpClient;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 网络工具控制器
 */
@Slf4j
@RequestMapping("/api/nwt")
@RestController
public class NwtController {
    @Autowired
    private WolDeviceService wolDeviceService;

    @Autowired
    private UpnpDevicesManager upnpDevicesManager;

    /**
     * 获取用户配置的WOL设备列表
     * @param uid   用户id
     * @return      WOL设备列表
     */
    @GetMapping("findWolByUid")
    @AllowAnonymous
    public JsonResult<List<WolDevice>> findByUid(@RequestParam("uid") @UID Long uid, @RequestParam(value = "checkOnline", defaultValue = "false") Boolean checkOnline) {
        if (checkOnline) {
            return JsonResultImpl.getInstance(wolDeviceService.findByUidAndCheckOnline(uid));
        } else {
            return JsonResultImpl.getInstance(wolDeviceService.findByUid(uid));
        }
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
    public JsonResult<?> wakeWolDevice(@RequestParam("id") Long id) throws IOException {
        wolDeviceService.wake(id);
        return JsonResult.emptySuccess();
    }

    /**
     * 批量删除WOL设备
     * @param ids   待删除的WOL涉笔ids
     * @return      删除数量
     */
    @PostMapping("batchDeleteWol")
    public JsonResult<Integer> batchDeleteWol(@RequestBody Collection<Long> ids) {
        return JsonResultImpl.getInstance(wolDeviceService.batchDelete(ids));
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

    /**
     * 获取系统中已发现的 UPnP 设备
     */
    @GetMapping("listUPnP")
    @RolesAllowed({"ADMIN"})
    public JsonResult<List<UpnpDevice>> listUPnP() {
        return JsonResultImpl.getInstance(upnpDevicesManager.getUpnpDeviceList());
    }

    /**
     * 获取UPnP设备自述中声明（device.iconList）的图标数据
     * @param usn   设备唯一标识
     * @param index 图标索引
     */
    @GetMapping("getUPnPIcon")
    public void getUPnPIcon(@RequestParam("usn") String usn,
                            @RequestParam("index") Integer index,
                            HttpServletResponse response
    ) throws IOException {
        UpnpDevice upnpDevice = upnpDevicesManager.getByRootUSN(usn);
        UpnpDescribe.Icon icon = Optional.ofNullable(upnpDevice)
                .map(d -> d.getDescribe().getDevice().getIconList())
                .map(icons -> {
                    if (index > icons.size() - 1) {
                        return null;
                    }
                    return icons.get(index);
                })
                .orElse(null);
        if (icon == null) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
            return;
        }

        String iconUrl;
        if (icon.getUrl().startsWith("http://") || icon.getUrl().startsWith("https://")) {
            iconUrl = icon.getUrl();
        } else {
            iconUrl = URLUtils.getBaseUrl(upnpDevice.getLocation()) + (icon.getUrl().startsWith("/") ? "" : "/") + icon.getUrl();
        }

        URL url = new URL(iconUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try (InputStream is = connection.getInputStream()) {
            Optional.ofNullable(icon.getMimetype())
                    .filter(StringUtils::hasText)
                    .ifPresent(response::setContentType);
            StreamUtils.copy(is, response.getOutputStream());
        } finally {
            connection.disconnect();
        }
    }

}
