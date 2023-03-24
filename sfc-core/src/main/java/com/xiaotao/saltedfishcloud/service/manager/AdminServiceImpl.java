package com.xiaotao.saltedfishcloud.service.manager;

import com.xiaotao.saltedfishcloud.common.SystemOverviewItemProvider;
import com.xiaotao.saltedfishcloud.constant.MQTopic;
import com.xiaotao.saltedfishcloud.enums.StoreMode;
import com.xiaotao.saltedfishcloud.config.SysProperties;
import com.xiaotao.saltedfishcloud.config.SysRuntimeConfig;
import com.xiaotao.saltedfishcloud.dao.mybatis.FileAnalyseDao;
import com.xiaotao.saltedfishcloud.model.ConfigNode;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.model.vo.SystemOverviewVO;
import com.xiaotao.saltedfishcloud.service.MQService;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.utils.SpringContextUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.File;
import java.util.*;

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

    @Autowired(required = false)
    private List<SystemOverviewItemProvider> itemProviderList;

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
