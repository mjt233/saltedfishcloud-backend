package com.sfc.nwt.upnp;

import com.sfc.nwt.upnp.event.NotifySSDPEvent;
import com.sfc.nwt.upnp.event.SSDPEventListener;
import com.sfc.nwt.upnp.event.SSDPEvent;
import com.sfc.nwt.upnp.model.NotifySsdpMessage;
import com.sfc.nwt.upnp.model.SsdpMessage;
import com.sfc.nwt.upnp.model.UpnpDevice;
import com.sfc.nwt.upnp.model.xml.UpnpDescribe;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/**
 * Upnp 设备管理器，用于维护已发现的 Upnp 设备列表
 */
@Slf4j
public class UpnpDevicesManager implements SSDPEventListener, SmartLifecycle {
    private final static String LOG_PREFIX = "[UPnP管理]";
    private final SSDPService SSDPService;

    private final ScheduledExecutorService aliveChecker = Executors.newScheduledThreadPool(1);

    /**
     * 记录已发现的 有效的根设备
     */
    private final Map<String, UpnpDevice> rootDeviceMap = new ConcurrentHashMap<>();

    private boolean isStarted;

    /**
     * 获取设备描述的线程池
     */
    protected Executor deviceDescExecutor = new ThreadPoolExecutor(
            0,
            Runtime.getRuntime().availableProcessors() * 16,
            45,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(1024)
    );

    public UpnpDevice getByRootUSN(String usn) {
        return rootDeviceMap.get(usn);
    }

    public UpnpDevicesManager(SSDPService SSDPService) {
        this.SSDPService = SSDPService;
    }

    /**
     * 开始监听 Upnp设备的组播消息
     */
    public void start() {
        if (this.isStarted) {
            return;
        }
        log.debug("{} 开始监听网络组播消息", LOG_PREFIX);
        this.isStarted = true;
        this.SSDPService.addEventListener(this);
        aliveChecker.scheduleAtFixedRate(this::checkAndUpdateDeviceAlive, 45, 45, TimeUnit.SECONDS);
    }

    protected void checkAndUpdateDeviceAlive() {
        long startDate = System.currentTimeMillis();
        log.debug("{} 开始检查 UPnP 是否存活", LOG_PREFIX);
        Deque<String> keys = new ConcurrentLinkedDeque<>();
        List<CompletableFuture<?>> futures;
        synchronized (rootDeviceMap) {
            futures = rootDeviceMap.entrySet()
                    .stream()
                    .map(entry -> {
                        String rootUSN = entry.getKey();
                        UpnpDevice upnpDevice = entry.getValue();
                        long now = System.currentTimeMillis();
                        if (upnpDevice.getExpireAt() > now) {
                            return CompletableFuture.completedFuture(null);
                        }
                        return getUpnpDescribe(upnpDevice.getLocation())
                                .thenAccept(upnpDescribe -> {
                                    // 更新设备描述和过期时间
                                    upnpDevice.setDescribe(upnpDescribe);
                                    upnpDevice.setExpireAt(now + upnpDevice.getCacheControlMaxAge() * 1000L);
                                })
                                .whenComplete((desc, throwable) -> {
                                    if (throwable != null) {
                                        String deviceName = Optional.ofNullable(upnpDevice.getDescribe())
                                                .map(UpnpDescribe::getDevice)
                                                .map(UpnpDescribe.Device::getFriendlyName)
                                                .orElse(upnpDevice.getLocation());
                                        log.error("{} 移除不可访问的UPnP设备 {} ", LOG_PREFIX, deviceName, throwable);
                                        keys.add(rootUSN);
                                    }
                                });
                    })
                    .toList();
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .whenComplete((unused, throwable) -> {
                    log.debug(
                            "{} UPnP 存活检查完成，耗时: {}s 存活设备: {} 失活设备: {}",
                            LOG_PREFIX,
                            System.currentTimeMillis() - (double)startDate,
                            rootDeviceMap.size() - keys.size(),
                            keys
                    );
                    if (!keys.isEmpty()) {
                        synchronized (rootDeviceMap) {
                            keys.forEach(rootDeviceMap::remove);
                        }
                    }
                });
    }

    /**
     * 获取所有已记录的 Upnp 设备列表
     */
    public List<UpnpDevice> getUpnpDeviceList() {
        return new ArrayList<>(rootDeviceMap.values());
    }

    @Override
    public void handleSSDPEvent(SSDPEvent<? extends SsdpMessage> event) {
        if (event instanceof NotifySSDPEvent notifyUpnpEvent) {
            NotifySsdpMessage notifyMsg = notifyUpnpEvent.getMessage();
            if(notifyMsg.isRootDevice()) {
                if (notifyMsg.isAlive()) {
                    this.addOrRefreshRootDevice(notifyMsg);
                } else if (notifyMsg.isByeBye()) {
                    this.removeRootDevice(notifyMsg);
                }
            }
        }
    }

    protected void removeRootDevice(NotifySsdpMessage message) {
        log.info("{} 设备 {} 发送下线广播",
                LOG_PREFIX,
                Optional.ofNullable(rootDeviceMap.get(message.getRootDeviceUSN()))
                        .map(e -> e.getDescribe().getDevice().getFriendlyName())
                        .orElseGet(message::getRootDeviceUSN)
        );

        this.rootDeviceMap.remove(message.getRootDeviceUSN());
    }

    /**
     * 获取 UPnP 设备描述
     */
    protected CompletableFuture<UpnpDescribe> getUpnpDescribe(String locationUrl) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                URL url = new URL(locationUrl);
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setConnectTimeout(2000);
                try (InputStream is = urlConnection.getInputStream()) {
                    String xmlDesc = StreamUtils.copyToString(is, StandardCharsets.UTF_8);
                    is.close();
                    return UpnpUtils.parseRootDesc(xmlDesc);
                } finally {
                    urlConnection.disconnect();
                }
            } catch (IOException e) {
                log.debug("{} 获取UPnP描述失败: {} 原因: {}", LOG_PREFIX, locationUrl, e.getMessage());
                throw new RuntimeException(e);
            }
        }, deviceDescExecutor);
    }

    /**
     * 收到 SSDP 响应消息后，将该消息对应的设备添加到已发现设备中，并获取设备描述。若无法获取设备描述则添加失败。
     */
    protected CompletableFuture<UpnpDevice> addOrRefreshRootDevice(NotifySsdpMessage message) {
        if(!StringUtils.hasText(message.getLocation())) {
            log.warn("{} NOTIFY消息缺少设备描述URL: {}", LOG_PREFIX, message);
            return CompletableFuture.failedFuture(new RuntimeException("NOTIFY消息缺少设备描述URL:\n" + message));
        }

        return getUpnpDescribe(message.getLocation())
                .thenApply(upnpDescribe -> {
                    UpnpDevice device = new UpnpDevice();
                    device.setDescribe(upnpDescribe);
                    device.setLocation(message.getLocation());
                    long now = System.currentTimeMillis();
                    device.setFoundAt(now);
                    device.setExpireAt(now);

                    if (message.getCacheControlMaxAge() > 0) {
                        device.setExpireAt(now + message.getCacheControlMaxAge() * 1000L);
                    }
                    if (!rootDeviceMap.containsKey(message.getRootDeviceUSN())) {
                        log.info("{} 设备: {} 成功获取UPnP描述: {}", LOG_PREFIX, upnpDescribe.getDevice().getFriendlyName(), message.getLocation());
                    }
                    rootDeviceMap.put(message.getRootDeviceUSN(), device);
                    return device;
                });
    }

    /**
     * 停止监听处理 Upnp设备的组播消息
     */
    public void stop() {
        if (!isStarted) {
            return;
        }
        log.debug("{} 停止监听UPnP设备消息", LOG_PREFIX);
        isStarted = false;
        this.aliveChecker.shutdown();
        this.SSDPService.removeEventListener(this);
    }

    @Override
    public boolean isRunning() {
        return isStarted;
    }
}
