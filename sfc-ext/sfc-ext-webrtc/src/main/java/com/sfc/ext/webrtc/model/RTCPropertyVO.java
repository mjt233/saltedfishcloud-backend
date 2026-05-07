package com.sfc.ext.webrtc.model;

public class RTCPropertyVO {

    private RTCProperty rawProperty;

    public RTCPropertyVO(RTCProperty rawProperty) {
        this.rawProperty = rawProperty;
    }

    public Boolean getUseIceServer() {
        return rawProperty.getUseIceServer();
    }

    public String getIceServerUrl() {
        return rawProperty.getIceServerUrl();
    }
}
