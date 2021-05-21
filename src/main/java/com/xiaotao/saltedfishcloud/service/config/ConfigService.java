package com.xiaotao.saltedfishcloud.service.config;

import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.config.StoreType;
import com.xiaotao.saltedfishcloud.dao.ConfigDao;
import com.xiaotao.saltedfishcloud.enums.ConfigName;
import com.xiaotao.saltedfishcloud.enums.ReadOnlyLevel;
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
            DiskConfig.setReadOnlyLevel(ReadOnlyLevel.DATA_CHECKING);
            configDao.setConfigure(StoreType.getConfigKey(), type.toString());
            diskConfig.setStoreType(type.toString());
            storeTypeSwitch.switchTo(type);
            DiskConfig.setReadOnlyLevel(null);
        } catch (RuntimeException e) {
            DiskConfig.setReadOnlyLevel(null);
            throw e;
        }
        return true;
    }

    /**
     * 设置系统注册邀请码
     * @param code  邀请码
     */
    public void setInviteRegCode(String code) {
        configDao.setConfigure(ConfigName.REG_CODE, code);
        DiskConfig.REG_CODE = code;
    }
}
