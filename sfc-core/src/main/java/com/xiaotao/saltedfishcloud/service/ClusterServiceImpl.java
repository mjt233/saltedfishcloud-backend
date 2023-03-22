package com.xiaotao.saltedfishcloud.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.xiaotao.saltedfishcloud.constant.MQTopic;
import com.xiaotao.saltedfishcloud.dao.redis.RedisDao;
import com.xiaotao.saltedfishcloud.model.ClusterNodeInfo;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.identifier.IdUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ClusterServiceImpl implements ClusterService, InitializingBean {
    private final long selfId = IdUtil.getId();

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private MQService mqService;

    @Autowired
    private RedisDao redisDao;

    private final static String KEY_PREFIX = "cluster::";

    private String getKey() {
        return KEY_PREFIX + selfId;
    }

    @Override
    public List<ClusterNodeInfo> listNodes() {
        Set<String> keys = redisDao.scanKeys(KEY_PREFIX + "*");

        return keys.stream().map(key -> redisTemplate.opsForValue().get(key)).filter(Objects::nonNull)
                .map(obj -> {
                    try {
                        return MapperHolder.parseAsJson(obj, ClusterNodeInfo.class);
                    } catch (JsonProcessingException e) {
                        throw new IllegalArgumentException(e);
                    }
                }).collect(Collectors.toList());
    }

    @Override
    public ClusterNodeInfo getSelf() {
        Runtime runtime = Runtime.getRuntime();
        String host = null;
        String ip = null;
        try {
            host = InetAddress.getLocalHost().getHostName();
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();

            StringJoiner sj = new StringJoiner(";");
            while (networkInterfaces.hasMoreElements()) {
                sj.add(networkInterfaces.nextElement().inetAddresses().map(InetAddress::getHostAddress).collect(Collectors.joining(";")));
            }
            ip = sj.toString();
        } catch (UnknownHostException | SocketException e) {
            e.printStackTrace();
            ip = e.getMessage();
        }
        return ClusterNodeInfo.builder()
                .cpu(runtime.availableProcessors())
                .id(selfId)
                .host(host)
                .ip(ip)
                .memory(runtime.maxMemory())
                .tempSpace(new File(PathUtils.getTempDirectory()).getFreeSpace())
                .build();
    }

    @Override
    public void registerSelf() {
        ClusterNodeInfo self = getSelf();
        Boolean success = redisTemplate.opsForValue().setIfAbsent(getKey(), self);
        redisTemplate.expire(getKey(), Duration.ofSeconds(30));
        if (Boolean.TRUE.equals(success)) {
            mqService.send(MQTopic.CLUSTER_NODE_ONLINE, self);
        }

    }

    @Override
    public void afterPropertiesSet() throws Exception {
        mqService.subscribe(MQTopic.CLUSTER_NODE_ONLINE, msg -> log.info("[集群管理]集群节点上线:{}", msg.toString()));
        registerSelf();
    }
}