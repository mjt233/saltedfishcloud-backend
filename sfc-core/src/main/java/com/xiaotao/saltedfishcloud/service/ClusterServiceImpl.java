package com.xiaotao.saltedfishcloud.service;

import com.xiaotao.saltedfishcloud.constant.MQTopic;
import com.xiaotao.saltedfishcloud.dao.redis.RedisDao;
import com.xiaotao.saltedfishcloud.model.ClusterNodeInfo;
import com.xiaotao.saltedfishcloud.model.RequestParam;
import com.xiaotao.saltedfishcloud.model.po.User;
import com.xiaotao.saltedfishcloud.utils.JwtUtils;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.xiaotao.saltedfishcloud.utils.identifier.IdUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
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

    @Autowired
    private Environment environment;

    @Autowired
    private RestTemplate restTemplate;

    private final static String KEY_PREFIX = "cluster::";

    private String getKey() {
        return KEY_PREFIX + selfId;
    }

    /**
     * 下次需要执行抓取集群节点信息的时间点
     */
    private long nextFetchTime = 0;

    /**
     * 缓存的集群节点信息列表
     */
    private List<ClusterNodeInfo> cacheNodeList = Collections.emptyList();

    @Override
    public List<ClusterNodeInfo> listNodes() {
        long now = System.currentTimeMillis();
        if (now <= nextFetchTime) {
            return cacheNodeList;
        }
        fetchNodes();
        return cacheNodeList;
    }

    /**
     * 抓取最新的集群节点信息列表
     */
    private synchronized void fetchNodes() {
        long now = System.currentTimeMillis();
        if (now <= nextFetchTime) {
            return;
        }
        Set<String> keys = redisDao.scanKeys(KEY_PREFIX + "*");

        cacheNodeList = keys.stream().map(key -> redisTemplate.opsForValue().get(key)).filter(Objects::nonNull)
                .map(obj -> {
                    try {
                        return MapperHolder.parseAsJson(obj, ClusterNodeInfo.class);
                    } catch (IOException e) {
                        throw new IllegalArgumentException(e);
                    }
                }).collect(Collectors.toUnmodifiableList());

        // 更新下次需要抓取节点信息的时间为 5~10秒钟后
        nextFetchTime = System.currentTimeMillis() + (5 + (new Random()).nextInt()%10 * 1000L);
    }

    @Override
    public int getNodeCount() {
        return listNodes().size();
    }

    @Override
    public ClusterNodeInfo getNodeById(Long id) {
        try {
            Object obj = redisTemplate.opsForValue().get(KEY_PREFIX + id);
            if (obj == null) {
                return null;
            }
            return MapperHolder.parseAsJson(obj, ClusterNodeInfo.class);
        } catch (IOException e) {
            log.error("节点信息解析错误：",e);
            return null;
        }
    }

    @Override
    public ClusterNodeInfo getSelf() {
        Runtime runtime = Runtime.getRuntime();
        String host = null;
        String ip;
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
        String port = environment.getProperty("server.port");
        return ClusterNodeInfo.builder()
                .cpu(runtime.availableProcessors())
                .id(selfId)
                .host(host)
                .httpPort(port == null ? null : Integer.valueOf(port))
                .ip(ip)
                .memory(runtime.maxMemory())
                .tempSpace(new File(PathUtils.getTempDirectory()).getFreeSpace())
                .build();
    }

    @Override
    public void registerSelf() {
        ClusterNodeInfo self = getSelf();
        Boolean success = redisTemplate.opsForValue().setIfAbsent(getKey(), self);
        redisTemplate.expire(getKey(), Duration.ofSeconds(10));
        if (Boolean.TRUE.equals(success)) {
            mqService.sendBroadcast(MQTopic.CLUSTER_NODE_ONLINE, self);
        }

    }

    @Override
    public void afterPropertiesSet() throws Exception {
        mqService.subscribeBroadcast(MQTopic.CLUSTER_NODE_ONLINE, msg -> log.info("[集群管理]集群节点上线:{}", msg.getBody().toString()));
        registerSelf();
    }

    @Override
    public <T> ResponseEntity<T> request(Long nodeId, RequestParam param, ParameterizedTypeReference<T> typeReference) {
        ClusterNodeInfo node = getNodeById(nodeId);

        // 构造Header，并添加当前用户token
        HttpHeaders headers = new HttpHeaders();
        User user = SecureUtils.getSpringSecurityUser();
        if (user != null) {
            headers.put(JwtUtils.AUTHORIZATION, Collections.singletonList(user.getToken()));
        }
        if (param.getHeaders() != null) {
            headers.putAll(param.getHeaders());
        }
        HttpEntity<Object> requestEntity = new HttpEntity<>(param.getBody(), headers);

        return restTemplate.exchange(
                node.getRequestUrl(param.getUrl()),
                param.getMethod(),
                requestEntity,
                typeReference,
                Optional.ofNullable(param.getParameters()).orElse(Collections.emptyMap())
        );
    }
}
