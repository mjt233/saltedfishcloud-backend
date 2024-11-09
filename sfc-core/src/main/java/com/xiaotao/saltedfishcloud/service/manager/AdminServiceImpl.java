package com.xiaotao.saltedfishcloud.service.manager;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.xiaotao.saltedfishcloud.common.SystemOverviewItemProvider;
import com.xiaotao.saltedfishcloud.config.SysProperties;
import com.xiaotao.saltedfishcloud.constant.MQTopic;
import com.xiaotao.saltedfishcloud.dao.mybatis.FileAnalyseDao;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.model.ClusterNodeInfo;
import com.xiaotao.saltedfishcloud.model.ConfigNode;
import com.xiaotao.saltedfishcloud.model.SystemInfoVO;
import com.xiaotao.saltedfishcloud.model.TimestampRecord;
import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultModel;
import com.xiaotao.saltedfishcloud.model.po.User;
import com.xiaotao.saltedfishcloud.model.vo.SystemOverviewVO;
import com.xiaotao.saltedfishcloud.service.ClusterService;
import com.xiaotao.saltedfishcloud.service.MQService;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.utils.JwtUtils;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.xiaotao.saltedfishcloud.utils.SpringContextUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.OperatingSystem;

import jakarta.annotation.Resource;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

@Service
public class AdminServiceImpl implements AdminService, InitializingBean {
    @Resource
    private FileAnalyseDao fileAnalyseDao;
    @Resource
    private SysProperties sysProperties;
    @Resource
    private DiskFileSystemManager diskFileSystemManager;
    @Resource
    private MQService mqService;
    @Resource
    private ClusterService clusterService;
    @Resource
    private RestTemplate restTemplate;

    private final ConcurrentLinkedQueue<TimestampRecord<SystemInfoVO>> systemInfoRecords = new ConcurrentLinkedQueue<>();

    @Autowired(required = false)
    private List<SystemOverviewItemProvider> itemProviderList;


    private double getCpuAvgLoad(CentralProcessor processor) {
        long[] prevTicks = processor.getSystemCpuLoadTicks();
        try {
            Thread.sleep(450);
        } catch (InterruptedException ignore) {}
        long[] ticks = processor.getSystemCpuLoadTicks();
        long nice = ticks[CentralProcessor.TickType.NICE.getIndex()] - prevTicks[CentralProcessor.TickType.NICE.getIndex()];
        long irq = ticks[CentralProcessor.TickType.IRQ.getIndex()] - prevTicks[CentralProcessor.TickType.IRQ.getIndex()];
        long softirq = ticks[CentralProcessor.TickType.SOFTIRQ.getIndex()] - prevTicks[CentralProcessor.TickType.SOFTIRQ.getIndex()];
        long steal = ticks[CentralProcessor.TickType.STEAL.getIndex()] - prevTicks[CentralProcessor.TickType.STEAL.getIndex()];
        long cSys = ticks[CentralProcessor.TickType.SYSTEM.getIndex()] - prevTicks[CentralProcessor.TickType.SYSTEM.getIndex()];
        long user = ticks[CentralProcessor.TickType.USER.getIndex()] - prevTicks[CentralProcessor.TickType.USER.getIndex()];
        long iowait = ticks[CentralProcessor.TickType.IOWAIT.getIndex()] - prevTicks[CentralProcessor.TickType.IOWAIT.getIndex()];
        long idle = ticks[CentralProcessor.TickType.IDLE.getIndex()] - prevTicks[CentralProcessor.TickType.IDLE.getIndex()];
        long totalCpu = user + nice + cSys + idle + iowait + irq + softirq + steal;
        if (totalCpu <= 0) {
            return 0;
        } else {
            return (1.0-(idle * 1.0 / totalCpu)) * 100;
        }

    }

    private SystemInfoVO getSelfCurSystemInfo(boolean full) {
        SystemInfo systemInfo = new SystemInfo();
        HardwareAbstractionLayer hardware = systemInfo.getHardware();
        CentralProcessor processor = hardware.getProcessor();
        GlobalMemory memory = hardware.getMemory();
        long memoryTotal = memory.getTotal();
        long memoryAvailable = memory.getAvailable();

        SystemInfoVO result = SystemInfoVO.builder()
                .cpuLoad(getCpuAvgLoad(processor))
                .totalMemory(memoryTotal)
                .usedMemory(memoryTotal - memoryAvailable)
                .memoryUsedRate((long) (((memoryTotal - memoryAvailable) / (double) memoryTotal) * 100))
                .build();
        if (!full) {
            return result;
        }

        OperatingSystem os = systemInfo.getOperatingSystem();
        result.setOs(os.getFamily() + " " + os.getVersionInfo());
        result.setCpu(processor.getProcessorIdentifier().getName().trim());
        result.setCpuLogicCount(processor.getLogicalProcessorCount());
        result.setCpuPhysicalCount(processor.getPhysicalProcessorCount());
        return result;
    }

    @Override
    public Collection<TimestampRecord<SystemInfoVO>> listSystemInfo(Long nodeId) {
        if (nodeId == null) {
            return Collections.unmodifiableCollection(systemInfoRecords);
        }
        ClusterNodeInfo node = clusterService.getNodeById(nodeId);
        if (node == null) {
            throw new IllegalArgumentException("节点不存在");
        }
        ResponseEntity<String> response = request(node.getRequestUrl("/api/admin/sys/listSystemInfo"), HttpMethod.GET);
        try {
            return MapperHolder.mapper.readValue(response.getBody(), new TypeReference<JsonResultModel<List<TimestampRecord<SystemInfoVO>>>>(){}).getData();
        } catch (JsonProcessingException e) {
            throw new JsonException("json解析错误" + e.getMessage());
        }

    }

    private ClusterNodeInfo getNode(Long nodeId) {
        return Optional.ofNullable(clusterService.getNodeById(nodeId)).orElseThrow(() -> new IllegalArgumentException("节点不存在"));
    }

    @Override
    public SystemInfoVO getCurSystemInfo(Long nodeId, boolean full) {
        // todo 实现一个专门用于节点间服务调用的服务，或者干脆直接玩一下Spring Cloud那一套
        if (nodeId == null) {
            return getSelfCurSystemInfo(full);
        }

        ClusterNodeInfo node = this.getNode(nodeId);
        ResponseEntity<String> response = request(node.getRequestUrl("/api/admin/sys/getCurSystemInfo"), HttpMethod.GET);
        try {
            return MapperHolder.mapper.readValue(response.getBody(), new TypeReference<JsonResultModel<SystemInfoVO>>() {}).getData();
        } catch (JsonProcessingException e) {
            throw new JsonException("json解析错误" + e.getMessage());
        }
    }

    private <T> ResponseEntity<String> request(String url, HttpMethod method) {
        HttpHeaders headers = new HttpHeaders();
        User user = SecureUtils.getSpringSecurityUser();
        if (user != null) {
            headers.put(JwtUtils.AUTHORIZATION, Collections.singletonList(user.getToken()));
        }
        HttpEntity<JsonResult<T>> httpEntity = new HttpEntity<>(headers);

        return restTemplate.exchange(
                url,
                method,
                httpEntity,
                new ParameterizedTypeReference<String>() {}
        );
    }

    @Override
    public synchronized void addSystemInfoRecord() {
        systemInfoRecords.add(TimestampRecord.<SystemInfoVO>builder()
                .timestamp(System.currentTimeMillis())
                .data(getCurSystemInfo(null, false))
                .build()
        );

        if (systemInfoRecords.size() > 360) {
            systemInfoRecords.poll();
        }

    }

    @Override
    public void cleanSystemInfo() {
        systemInfoRecords.clear();
    }

    /**
     * 获取系统当前预览数据
     */
    private SystemOverviewVO getCurOverviewData() {
        SystemOverviewVO vo = new SystemOverviewVO();
        vo.setFileSystemStatus(diskFileSystemManager.getMainFileSystem().getStatus());
        if (itemProviderList == null || itemProviderList.isEmpty()) {
            vo.setSystemStatus(Collections.emptyList());
            return vo;
        }
        Map<String, ConfigNode> existMap = new HashMap<>();
        List<ConfigNode> systemStatus = new ArrayList<>();
        vo.setSystemStatus(systemStatus);
        itemProviderList.stream()
                .sorted(Comparator.comparing(SystemOverviewItemProvider::getProvideOrder))
                .forEach(provider -> Optional.ofNullable(provider.provideItem(existMap))
                        .orElse(Collections.emptyList())
                        .forEach(configNode -> {
                            // 若存在相同的分类，则合并
                            ConfigNode existCategoryNode = existMap.get(configNode.getName());
                            if (existCategoryNode != null) {
                                if(existCategoryNode.getNodes() == null) {
                                    existCategoryNode.setNodes(configNode.getNodes());
                                }
                            } else {
                                existMap.put(configNode.getName(), configNode);
                            }
                            systemStatus.add(configNode);
                        })
                );
        return vo;
    }

    @Override
    public SystemOverviewVO getOverviewData(Long nodeId) {
        if (nodeId == null) {
            return getCurOverviewData();
        }

        ClusterNodeInfo node = getNode(nodeId);
        ResponseEntity<String> response = request(node.getRequestUrl("/api/admin/sys/overview"), HttpMethod.GET);
        try {
            return MapperHolder.mapper.readValue(response.getBody(), new TypeReference<JsonResultModel<SystemOverviewVO>>() {}).getData();
        } catch (JsonProcessingException e) {
            throw new JsonException("json解析错误" + e.getMessage());
        }
    }

    /**
     * 真正执行重启
     */
    private void doRestart() {
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(500);
                SpringContextUtils.restart();
            } catch (InterruptedException ignore) {
            }
        });
        thread.start();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        mqService.subscribeBroadcast(MQTopic.RESTART, msg -> doRestart());
    }

    @Override
    public void restart(boolean withCluster) {
        if (!withCluster) {
            doRestart();
        } else {
            mqService.sendBroadcast(MQTopic.RESTART, "");
        }
    }
}
