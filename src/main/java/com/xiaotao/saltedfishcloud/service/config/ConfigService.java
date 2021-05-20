package com.xiaotao.saltedfishcloud.service.config;

import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.config.StoreType;
import com.xiaotao.saltedfishcloud.dao.ConfigDao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.io.IOException;

@Service
@Slf4j
public class ConfigService {
    @Resource
    private ConfigDao configDao;
    @Resource
    private StoreTypeSwitch storeTypeSwitch;
    @Resource
    private DiskConfig diskConfig;

    /**
     * 设置存储类型
     * @param type 存储类型
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean setStoreType(StoreType type) throws IOException {
        String origin = configDao.getConfigure(StoreType.getConfigKey());
        if (origin == null) {
            throw new IllegalStateException("数据配置表无信息，请重启服务器");
        }
        StoreType storeType = StoreType.valueOf(origin);
        if (storeType == type) {
            log.info("忽略的存储切换：" + storeType.toString() + " -> " + type.toString());
            return false;
        }
        log.info("存储切换：" + storeType.toString() + " -> " + type.toString());
        try {
            DiskConfig.setReadOnlyBlock(true);
            configDao.setConfigure(StoreType.getConfigKey(), type.toString());
            diskConfig.setStoreType(type.toString());
            storeTypeSwitch.switchTo(type);
            DiskConfig.setReadOnlyBlock(false);
        } catch (RuntimeException e) {
            DiskConfig.setReadOnlyBlock(false);
            throw e;
        }
        return true;
    }
}
