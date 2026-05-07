package com.sfc.nwt.upnp.event;

import com.sfc.nwt.upnp.model.NotifySsdpMessage;

import java.net.InetAddress;

public class NotifySSDPEvent extends SSDPEvent<NotifySsdpMessage> {
    public NotifySSDPEvent(String rawResponse, NotifySsdpMessage message, InetAddress address) {
        super(rawResponse, message, address);
    }
}
