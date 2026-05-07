package com.sfc.nwt.controller;

import com.sfc.nwt.model.NetworkInterfaceInfo;
import com.sfc.nwt.model.po.WolDevice;
import com.sfc.nwt.service.UpnpService;
import com.sfc.nwt.service.WolDeviceService;
import com.sfc.nwt.upnp.UpnpDevicesManager;
import com.sfc.nwt.upnp.UpnpUtils;
import com.sfc.nwt.upnp.control.MediaRendererControlPoint;
import com.sfc.nwt.upnp.model.ServiceActionInvokeParam;
import com.sfc.nwt.upnp.model.UpnpDevice;
import com.sfc.nwt.upnp.model.xml.device.UpnpDescribe;
import com.sfc.nwt.upnp.model.xml.service.av.Scpd;
import com.sfc.nwt.utils.NetworkUtils;
import com.xiaotao.saltedfishcloud.annotations.AllowAnonymous;
import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import com.xiaotao.saltedfishcloud.validator.annotations.UID;
import jakarta.annotation.security.RolesAllowed;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.SocketException;
import java.net.URL;
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

    @Autowired
    private UpnpService upnpService;

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
    public JsonResult<List<UpnpDevice>> listUPnP(@RequestParam(value = "forceCheckAlive", defaultValue = "false") Boolean isForceCheckAlive) {
        if (Boolean.TRUE.equals(isForceCheckAlive)) {
            upnpDevicesManager.checkAndUpdateDeviceAlive(true).join();
        }
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

        URL url = UpnpUtils.getServiceOrDeviceUrl(upnpDevice.getLocation(), icon.getUrl());
        Optional.ofNullable(icon.getMimetype())
                .filter(StringUtils::hasText)
                .ifPresent(response::setContentType);
        byte[] iconBytes = UpnpUtils.getContentByUrl(url);
        response.setContentLength(iconBytes.length);
        response.getOutputStream().write(iconBytes);
    }

    /**
     * （管理员调试用）获取 UPnP 设备的服务描述参数
     * @param usn   根设备UDN/USN
     * @param serviceType   服务类型
     */
    @GetMapping("getUpnpServiceScpd")
    @RolesAllowed("ADMIN")
    public JsonResult<Scpd> getUpnpServiceScpd(@RequestParam("usn") String usn,
                                                 @RequestParam("serviceType") String serviceType
                                               ) throws IOException {
        return JsonResultImpl.getInstance(upnpService.getUpnpServiceScpd(usn, serviceType));
    }

    /**
     * （管理员调试用）对 UPnP 的服务发起调用
     * @return 接口响应原始报文
     */
    @PostMapping("invokeUpnpService")
    @RolesAllowed("ADMIN")
    public JsonResult<UpnpUtils.SimpleHttpResponse> invokeUpnpService(@RequestBody ServiceActionInvokeParam param) throws IOException {
        return JsonResultImpl.getInstance(upnpService.invokeUpnpService(param));
    }

    /**
     * 获取多媒体 UPnP 设备支持的媒体协议列表
     * @param usn   根设备UDN/USN
     */
    @GetMapping("getMediaUpnpProtocolInfo")
    public JsonResult<List<String>> getMediaUpnpProtocolInfo(@RequestParam("usn") String usn) throws IOException {
        return JsonResultImpl.getInstance(new MediaRendererControlPoint(upnpDevicesManager.getByRootUSN(usn)).getProtocolInfo());
    }

    /**
     * 视频投屏<br>
     * 单独一个接口而不是使用通用的 UPnP 服务调用是为了方便后续进一步细分权限控制
     * @param usn   媒体播放器根设备 UDN
     * @param uri   要播放的 URI
     * @param instanceId 播放器实例 ID
     */
    @PostMapping("castMedia")
    @RolesAllowed("ADMIN")
    public JsonResult<?> castMedia(@RequestParam("usn") String usn,
                          @RequestParam("uri") String uri,
                          @RequestParam(value = "instanceId", required = false) String instanceId) throws IOException {
        new MediaRendererControlPoint(upnpDevicesManager.getByRootUSN(usn)).castMedia(instanceId, uri);
        return JsonResult.emptySuccess();
    }
}
