package com.xiaotao.saltedfishcloud.service.manager;

import com.xiaotao.saltedfishcloud.common.SystemOverviewItemProvider;
import com.xiaotao.saltedfishcloud.enums.StoreMode;
import com.xiaotao.saltedfishcloud.config.SysProperties;
import com.xiaotao.saltedfishcloud.config.SysRuntimeConfig;
import com.xiaotao.saltedfishcloud.dao.mybatis.FileAnalyseDao;
import com.xiaotao.saltedfishcloud.model.ConfigNode;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.model.vo.SystemOverviewVO;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.File;
import java.util.*;

@Service
public class AdminServiceImpl implements AdminService {
    @Resource
    private FileAnalyseDao fileAnalyseDao;
    @Resource
    private SysProperties sysProperties;
    @Resource
    private DiskFileSystemManager diskFileSystemManager;

    @Autowired(required = false)
    private List<SystemOverviewItemProvider> itemProviderList;

    @Override public SystemOverviewVO getOverviewData() {
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
     * 获取系统存储状态
     * @deprecated 已弃用
     */
    @Deprecated
    public Map<String, Object> getStoreState() {
        LinkedHashMap<String, Object> data = JsonResultImpl.getDataMap();
        File storeRoot = new File(sysProperties.getStore().getRoot());
        File publicRoot = new File(sysProperties.getStore().getPublicRoot());
        long userTotalSize = fileAnalyseDao.getUserTotalSize();
        long realTotalUserSize = sysProperties.getStore().getMode() == StoreMode.UNIQUE ? fileAnalyseDao.getRealTotalUserSize() : userTotalSize;
        long publicTotalSize = fileAnalyseDao.getPublicTotalSize();

        data.put("store_mode", sysProperties.getStore().getMode());
        data.put("file_count", fileAnalyseDao.getFileCount());
        data.put("dir_count", fileAnalyseDao.getDirCount());
        data.put("real_user_size", realTotalUserSize);
        data.put("total_user_size", userTotalSize);
        data.put("total_public_size", publicTotalSize);
        data.put("store_total_space", storeRoot.getTotalSpace());
        data.put("store_free_space", storeRoot.getFreeSpace());
        data.put("public_total_space", publicRoot.getTotalSpace());
        data.put("public_free_space", publicRoot.getFreeSpace());
        data.put("store_root", storeRoot.getPath());
        data.put("public_root", publicRoot.getPath());
        data.put("read_only", SysRuntimeConfig.getInstance().getProtectModeLevel());
        return data;
    }
}
