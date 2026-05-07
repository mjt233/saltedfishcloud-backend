package com.sfc.nwt.upnp.event;

import com.sfc.nwt.upnp.model.SsdpMessage;

@FunctionalInterface
public interface SSDPEventListener {
    void handleSSDPEvent(SSDPEvent<? extends SsdpMessage> event);
}
