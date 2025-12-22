package com.sfc.nwt.upnp;

import com.sfc.nwt.upnp.model.xml.device.UpnpDescribe;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

class UpnpUtilsTest {

    @Test
    void parseRootDesc() throws IOException {
        ClassPathResource resource = new ClassPathResource("ssdp/rootDeviceResponse.xml");
        try(InputStream is = resource.getInputStream()) {
            String xml = StreamUtils.copyToString(is, StandardCharsets.UTF_8);
            UpnpDescribe describe = UpnpUtils.parseRootDesc(xml);
            Assertions.assertEquals("XIAOTAO-AMD-YES", describe.getDevice().getFriendlyName());
            Assertions.assertEquals(3, describe.getDevice().getServiceList().size());
        }

    }
}