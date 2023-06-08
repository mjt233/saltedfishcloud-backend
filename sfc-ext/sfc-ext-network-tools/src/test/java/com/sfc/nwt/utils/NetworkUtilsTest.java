package com.sfc.nwt.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Arrays;

class NetworkUtilsTest {

    @Test
    void getAllConnectedInterface() throws SocketException {
        byte[] ff = new byte[]{(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff};

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
            Assertions.assertArrayEquals(macBytes, NetworkUtils.macHexToBinary(macString));
            byte[] magicPacket = NetworkUtils.getMagicPacket(macString);
            Assertions.assertArrayEquals(ff, Arrays.copyOfRange(magicPacket, 0, 6));
            Assertions.assertArrayEquals(macBytes, Arrays.copyOfRange(magicPacket, magicPacket.length - 6, magicPacket.length));
            Assertions.assertEquals(6 + 6*16, magicPacket.length);
        }
    }
}