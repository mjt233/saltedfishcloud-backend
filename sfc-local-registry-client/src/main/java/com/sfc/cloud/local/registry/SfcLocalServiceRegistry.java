package com.sfc.cloud.local.registry;

import com.sfc.cloud.local.registry.model.SfcLocalRegisterResult;
import com.sfc.cloud.local.registry.model.SfcLocalRegistryException;
import com.sfc.cloud.local.registry.config.SfcLocalRegistryProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.cloud.client.serviceregistry.ServiceRegistry;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * local 注册中心的客户端注册实现。
 * <p>
 * 注册成功后启动守护心跳线程；心跳失败（含 core 重启导致实例丢失）会自动重新注册。
 * 关闭时先尽力注销，再停止心跳线程。
 */
@Slf4j
public class SfcLocalServiceRegistry implements ServiceRegistry<SfcLocalRegistration>, DisposableBean {
    /** 日志前缀 */
    private static final String LOG_PREFIX = "[SFC local registry]";

    /** HTTP 客户端 */
    private final SfcLocalRegistryHttpClient httpClient;

    /** 客户端配置 */
    private final SfcLocalRegistryProperties properties;

    /** 当前实例注册信息 */
    private final AtomicReference<SfcLocalRegistration> registration = new AtomicReference<>();

    /** 是否已被服务端确认注册 */
    private final AtomicBoolean registered = new AtomicBoolean(false);

    /** 是否处于需要发送心跳的状态 */
    private final AtomicBoolean active = new AtomicBoolean(false);

    /** 最近一次错误消息，用于避免重复刷日志 */
    private final AtomicReference<String> lastError = new AtomicReference<>();

    /** 调度器锁 */
    private final Object schedulerLock = new Object();

    /** 心跳调度器 */
    private ScheduledExecutorService scheduler;

    /** 心跳任务 */
    private ScheduledFuture<?> heartbeatFuture;

    /** 是否已关闭 */
    private volatile boolean closed;

    /**
     * 创建客户端注册实现。
     *
     * @param httpClient HTTP 客户端
     * @param properties 客户端配置
     */
    public SfcLocalServiceRegistry(SfcLocalRegistryHttpClient httpClient,
                                   SfcLocalRegistryProperties properties) {
        this.httpClient = httpClient;
        this.properties = properties;
    }

    /**
     * 注册实例。首次注册失败不会抛出异常，心跳线程会持续重试。
     *
     * @param registration 实例注册信息
     */
    @Override
    public void register(SfcLocalRegistration registration) {
        if (registration == null) {
            throw new IllegalArgumentException("注册信息不能为空");
        }
        this.registration.set(registration);
        this.active.set(true);
        this.registered.set(false);
        startScheduler();
        tryRegister();
    }

    /**
     * 注销实例并停止心跳。
     *
     * @param registration 实例注册信息
     */
    @Override
    public void deregister(SfcLocalRegistration registration) {
        active.set(false);
        registered.set(false);
        cancelHeartbeat();
        SfcLocalRegistration current = registration == null ? this.registration.get() : registration;
        if (current == null) {
            return;
        }
        try {
            httpClient.deregister(current.getServiceId(), current.getInstanceId());
            log.info("{}已注销 serviceId={} instanceId={}", LOG_PREFIX,
                    current.getServiceId(), current.getInstanceId());
        } catch (RuntimeException e) {
            log.debug("{}注销实例失败 serviceId={} instanceId={}：{}", LOG_PREFIX,
                    current.getServiceId(), current.getInstanceId(), e.getMessage());
        }
    }

    /**
     * 停止心跳线程并注销实例。
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        active.set(false);
        registered.set(false);
        cancelHeartbeat();
        SfcLocalRegistration current = registration.getAndSet(null);
        if (current != null) {
            try {
                httpClient.deregister(current.getServiceId(), current.getInstanceId());
            } catch (RuntimeException e) {
                log.debug("{}关闭时注销实例失败：{}", LOG_PREFIX, e.getMessage());
            }
        }
        synchronized (schedulerLock) {
            if (scheduler != null) {
                scheduler.shutdownNow();
                scheduler = null;
            }
        }
    }

    /**
     * Spring 容器关闭时停止心跳线程。
     */
    @Override
    public void destroy() {
        close();
    }

    /**
     * 更新实例状态：{@code UP} 重新注册，{@code DOWN} 注销。
     *
     * @param registration 实例注册信息
     * @param status       状态值
     */
    @Override
    public void setStatus(SfcLocalRegistration registration, String status) {
        if ("UP".equalsIgnoreCase(status)) {
            register(registration);
            return;
        }
        if ("DOWN".equalsIgnoreCase(status)) {
            deregister(registration);
            return;
        }
        throw new IllegalArgumentException("不支持的实例状态：" + status);
    }

    /**
     * 查询实例状态。
     *
     * @param registration 实例注册信息
     * @param <T>          状态类型
     * @return {@code UP} 或 {@code DOWN}
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> T getStatus(SfcLocalRegistration registration) {
        return (T) (registered.get() ? "UP" : "DOWN");
    }

    /**
     * 延迟启动心跳调度器。
     */
    private void startScheduler() {
        synchronized (schedulerLock) {
            if (closed) {
                return;
            }
            if (scheduler == null || scheduler.isShutdown()) {
                scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread thread = new Thread(r, "sfc-local-registry-heartbeat");
                    thread.setDaemon(true);
                    return thread;
                });
            }
            if (heartbeatFuture == null || heartbeatFuture.isCancelled() || heartbeatFuture.isDone()) {
                long interval = properties.getHeartbeatInterval() == null
                        ? 10_000L
                        : Math.max(1_000L, properties.getHeartbeatInterval().toMillis());
                heartbeatFuture = scheduler.scheduleWithFixedDelay(
                        this::tick, interval, interval, TimeUnit.MILLISECONDS);
            }
        }
    }

    /**
     * 取消当前心跳任务，调度器本身保留以便重新注册时复用。
     */
    private void cancelHeartbeat() {
        synchronized (schedulerLock) {
            if (heartbeatFuture != null) {
                heartbeatFuture.cancel(false);
                heartbeatFuture = null;
            }
        }
    }

    /**
     * 心跳调度任务：未注册时尝试注册，已注册时发送心跳。
     */
    private void tick() {
        if (closed || !active.get()) {
            return;
        }
        SfcLocalRegistration current = registration.get();
        if (current == null) {
            return;
        }
        if (!registered.get()) {
            tryRegister();
            return;
        }
        try {
            long ttl = httpClient.heartbeat(current.getServiceId(), current.getInstanceId());
            clearError();
            log.debug("{}心跳成功 serviceId={} instanceId={} ttl={}s", LOG_PREFIX,
                    current.getServiceId(), current.getInstanceId(), ttl);
        } catch (SfcLocalRegistryException e) {
            registered.set(false);
            logFailure(e);
            if (e.isNotFound()) {
                tryRegister();
            }
        } catch (RuntimeException e) {
            registered.set(false);
            logFailure(e);
        }
    }

    /**
     * 尝试注册当前实例。
     */
    private void tryRegister() {
        SfcLocalRegistration current = registration.get();
        if (current == null || closed || !active.get()) {
            return;
        }
        try {
            SfcLocalRegisterResult result = httpClient.register(current);
            registered.set(true);
            clearError();
            log.info("{}注册成功 serviceId={} instanceId={} ttl={}s", LOG_PREFIX,
                    current.getServiceId(), current.getInstanceId(), result.getTtlSeconds());
        } catch (RuntimeException e) {
            registered.set(false);
            logFailure(e);
        }
    }

    /**
     * 仅在错误消息变化时输出一次错误日志，避免心跳重试刷屏。
     *
     * @param error 异常
     */
    private void logFailure(RuntimeException error) {
        String message = error.getMessage();
        if (!Objects.equals(lastError.getAndSet(message), message)) {
            log.error("{}注册/心跳失败：{}", LOG_PREFIX, message);
            if (log.isDebugEnabled()) {
                log.debug("{}注册/心跳异常明细", LOG_PREFIX, error);
            }
        }
    }

    /**
     * 清除最近一次错误记录。
     */
    private void clearError() {
        lastError.set(null);
    }
}
