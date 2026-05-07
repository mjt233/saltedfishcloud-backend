package com.sfc.ext.webrtc.enums;

/**
 * WebRTC SDP/CANDIDATE 或其他数据交换消息的数据类型
 */
public enum MessageDataType {
    CANDIDATE, OFFER, ANSWER,

    /**
     * 会话终止
     */
    INTERRUPT,

    /**
     * 本端实际id
     */
    PEER_ID,

    /**
     * 需要重定向到新的对端id(peerId)进行交互
     */
    REDIRECT_NEW_PEER,

    /**
     * 操作拒绝
     */
    DENY,

    /**
     * 其他自定义的业务普通消息
     */
    BUSINESS_MESSAGE
}
