package com.sfc.nwt.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.NetworkInterface;
import java.net.SocketException;

class NetworkUtilsTest {

    @Test
    void getAllConnectedInterface() throws SocketException {
        for (NetworkInterface networkInterface : NetworkUtils.getAllConnectedInterface()) {
            byte[] macBytes = networkInterface.getHardwareAddress();
            String macString = NetworkUtils.macByteToString(macBytes, ':');
            System.out.println(networkInterface.getName());
            System.out.println("  MAC: " + macString);
            networkInterface.getInterfaceAddresses().stream().map(e -> e.getAddress().getHostAddress()).forEach(ip -> {
                System.out.println("    " + ip);
            });

            if (macString == null) {
                continue;
            }
            Assertions.assertArrayEquals(macBytes, NetworkUtils.macHexToByte(macString));
        }
    }
}