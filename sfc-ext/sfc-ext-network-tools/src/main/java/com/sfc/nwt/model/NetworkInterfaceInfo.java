package com.sfc.nwt.model;

import com.sfc.nwt.utils.NetworkUtils;
import lombok.Data;

import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Data
public class NetworkInterfaceInfo {
    private String name;

    private String displayName;

    private String mac;

    private List<String> ipList;

    private List<String> broadcastAddressList;

    private Integer index;

    private Integer MTU;

    public static NetworkInterfaceInfo of(NetworkInterface networkInterface)  {
        NetworkInterfaceInfo info = new NetworkInterfaceInfo();
        info.name = networkInterface.getName();
        info.displayName = networkInterface.getDisplayName();
        try {
            info.mac = NetworkUtils.macByteToString(networkInterface.getHardwareAddress(), ':');
        } catch (SocketException ignore) {}

        info.ipList = networkInterface.inetAddresses().map(InetAddress::getHostAddress).collect(Collectors.toList());
        info.broadcastAddressList = networkInterface.getInterfaceAddresses().stream()
                .map(InterfaceAddress::getBroadcast)
                .filter(Objects::nonNull)
                .map(InetAddress::getHostAddress)
                .distinct()
                .collect(Collectors.toList());
        info.index = networkInterface.getIndex();
        try {
            info.MTU = networkInterface.getMTU();
        } catch (SocketException ignore) { }
        return info;
    }
}
