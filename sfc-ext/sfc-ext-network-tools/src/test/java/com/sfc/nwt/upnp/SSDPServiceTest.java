package com.sfc.nwt.upnp;

import com.sfc.nwt.upnp.impl.SSDPServiceImpl;
import org.junit.jupiter.api.Test;

import java.io.IOException;

class SSDPServiceTest {

    @Test
    void start() throws IOException, InterruptedException {
        SSDPService SSDPService = new SSDPServiceImpl();
        UpnpDevicesManager upnpDevicesManager = new UpnpDevicesManager(SSDPService);
        upnpDevicesManager.start();
        SSDPService.start();
        Thread.sleep(1000 * 60);
    }
}