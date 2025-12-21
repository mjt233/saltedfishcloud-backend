package com.sfc.nwt.upnp.model;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;

@Data
public class SsdpMessage {

    /**
     * 响应头的目标组播地址
     */
    private String host;

    /**
     * 设备描述 URL
     */
    private String location;

    /**
     * 消息的设备类型
     */
    private String NT;

    /**
     * 消息的子类型（定义消息的行为）
     */
    private String NTS;

    /**
     * 设备服务器信息
     */
    private String server;

    /**
     * 唯一服务名称标识
     */
    private String USN;


    /**
     * ssdp 消息方法/动作
     */
    private String ssdpMethod;
    private Map<String, String> attributes = new HashMap<>();

    public void setAttributes(String key, String value) {
        attributes.put(key, value);
    }

    public SsdpMessage() {

    }

    /**
     * 通过解析 Ssdp 报文来实例化
     */
    public SsdpMessage(String rawResponseBody) {
        Scanner scanner = new Scanner(rawResponseBody);
        scanner.useDelimiter("\r\n");

        // 读取第一行
        String method = Optional.ofNullable(scanner.nextLine())
                .map(line -> line.split(" ")[0])
                .orElseThrow(() -> new IllegalArgumentException("is not ssdp response"));
        this.setSsdpMethod(method);

        String line;
        while ( scanner.hasNext() ) {
            line = scanner.nextLine();
            String[] s = line.split(": ", 2);
            if (s.length < 2) {
                continue;
            }
            processHeader(s[0], s[1]);
        }
    }

    /**
     * 处理在解析 ssdp 消息时的头信息
     */
    protected void processHeader(String key, String value) {
        switch (key.toUpperCase()) {
            case "HOST": this.setHost(value);break;
            case "LOCATION": this.setLocation(value);break;
            case "NT": this.setNT(value);break;
            case "NTS": this.setNTS(value);break;
            case "SERVER": this.setServer(value);break;
            case "USN": this.setUSN(value);break;
            default: this.setAttributes(key, value);
        }
    }
}
