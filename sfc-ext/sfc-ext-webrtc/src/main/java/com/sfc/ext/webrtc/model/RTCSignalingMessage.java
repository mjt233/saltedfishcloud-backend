package com.sfc.ext.webrtc.model;

import com.sfc.ext.webrtc.enums.MessageDataType;
import lombok.Data;

/**
 * WebRTC 信令服务数据交换消息
 */
@Data
public class RTCSignalingMessage {
    /**
     * 对端标识
     */
    private String toPeerId;

    /**
     * 请求端标识
     */
    private String fromPeerId;

    private MessageDataType dataType;
    private Object data;
}
