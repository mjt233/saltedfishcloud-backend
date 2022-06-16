package com.xiaotao.saltedfishcloud.service.manager;

import com.xiaotao.saltedfishcloud.enums.StoreMode;
import com.xiaotao.saltedfishcloud.config.SysProperties;
import com.xiaotao.saltedfishcloud.config.SysRuntimeConfig;
import com.xiaotao.saltedfishcloud.dao.mybatis.FileAnalyseDao;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

// @TODO 规范配置参数命名
@Service
public class AdminService {
    @Resource
    private FileAnalyseDao fileAnalyseDao;
    @Resource
    private SysProperties sysProperties;

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
