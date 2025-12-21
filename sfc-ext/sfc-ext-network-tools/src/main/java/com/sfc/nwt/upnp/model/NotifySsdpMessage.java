package com.sfc.nwt.upnp.model;

import com.sfc.nwt.upnp.constants.UpnpConstants;
import com.sfc.nwt.upnp.constants.UpnpResponseType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * ssdp 协议响应通知消息(NOTIFY)
 */
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class NotifySsdpMessage extends SsdpMessage {

    /**
     * 缓冲可保持的时长（秒）
     */
    private int cacheControlMaxAge;

    /**
     * 使用时是否必须要重新验证
     */
    private boolean isMustRevalidate;

    public NotifySsdpMessage() {
    }

    public NotifySsdpMessage(String rawResponseBody) {
        super(rawResponseBody);
    }

    /**
     * 当前消息的子通知类型是否为 bye bye（设备下线通知）
     */
    public boolean isByeBye() {
        return "ssdp:byebye".equals(getNTS());
    }

    /**
     * 当前消息的子通知类型是否为 alive（设备在线/上线通知）
     */
    public boolean isAlive() {
        return "ssdp:alive".equals(getNTS());
    }

    /**
     * 是否为根设备
     */
    public boolean isRootDevice() {
        return UpnpConstants.SsdpType.ROOT_DEVICES.equals(getNT());
    }



    /**
     * 获取所属根设备的USN值
     */
    public String getRootDeviceUSN() {
        String usn = getUSN();
        int idx = usn.indexOf("::");
        if (idx == -1) {
            return usn;
        }
        return usn.substring(0, idx);
    }

    @Override
    protected void processHeader(String key, String value) {
        super.processHeader(key, value);
        if ("CACHE-CONTROL".equalsIgnoreCase(key)) {
            for (String cacheVal : value.split(", ")) {
                if (cacheVal.contains("max-age")) {
                    this.setCacheControlMaxAge(Integer.valueOf(cacheVal.split("=")[1]));
                } else if ("no-cache".equals(cacheVal)) {
                    this.setCacheControlMaxAge(0);
                } else if ("must-revalidate".equals(cacheVal)) {
                    this.setMustRevalidate(true);
                }
            }
        }
    }
}
