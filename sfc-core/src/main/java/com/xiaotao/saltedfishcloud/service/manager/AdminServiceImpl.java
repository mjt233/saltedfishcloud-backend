package com.xiaotao.saltedfishcloud.service.manager;

import com.xiaotao.saltedfishcloud.common.SystemOverviewItemProvider;
import com.xiaotao.saltedfishcloud.config.SysProperties;
import com.xiaotao.saltedfishcloud.constant.MQTopic;
import com.xiaotao.saltedfishcloud.dao.mybatis.FileAnalyseDao;
import com.xiaotao.saltedfishcloud.model.ConfigNode;
import com.xiaotao.saltedfishcloud.model.SystemInfoVO;
import com.xiaotao.saltedfishcloud.model.TimestampRecord;
import com.xiaotao.saltedfishcloud.model.vo.SystemOverviewVO;
import com.xiaotao.saltedfishcloud.service.MQService;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.utils.SpringContextUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.OperatingSystem;

import javax.annotation.Resource;
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

    private final ConcurrentLinkedQueue<TimestampRecord<SystemInfoVO>> systemInfoRecords = new ConcurrentLinkedQueue<>();

    @Autowired(required = false)
    private List<SystemOverviewItemProvider> itemProviderList;

    @Override
    public Collection<TimestampRecord<SystemInfoVO>> listSystemInfo() {
        return Collections.unmodifiableCollection(systemInfoRecords);
    }

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

    @Override
    public SystemInfoVO getCurSystemInfo(boolean full) {
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
    public synchronized void addSystemInfoRecord() {
        systemInfoRecords.add(TimestampRecord.<SystemInfoVO>builder()
                .timestamp(System.currentTimeMillis())
                .data(getCurSystemInfo(false))
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

    @Override
    public SystemOverviewVO getOverviewData() {
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
        mqService.subscribe(MQTopic.RESTART, msg -> doRestart());
    }

    @Override
    public void restart(boolean withCluster) {
        if (!withCluster) {
            doRestart();
        } else {
            mqService.send(MQTopic.RESTART, "");
        }
    }
}
