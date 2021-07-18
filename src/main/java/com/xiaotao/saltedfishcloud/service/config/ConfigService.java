package com.xiaotao.saltedfishcloud.service.config;

import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.config.StoreType;
import com.xiaotao.saltedfishcloud.dao.ConfigDao;
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
     * 从配置表读取一个配置项的值
     * @param key   配置名
     * @return      结果
     */
    public String getConfig(ConfigName key) {
        return configDao.getConfigure(key);
    }

    /**
     * 设置一个配置项
     * @param key       配置项
     * @param value     配置值
     */
    public boolean setConfig(ConfigName key, String value) throws IOException {
        switch (key) {
            case STORE_TYPE: return setStoreType(StoreType.valueOf(value));
            case REG_CODE: setInviteRegCode(value); return true;
            default:
                configDao.setConfigure(key, value);
        }
        return true;
    }
    /**
     * 设置一个配置项
     * @param key       配置项
     * @param value     配置值
     */
    public boolean setConfig(String key, String value) throws IOException {
        return setConfig(ConfigName.valueOf(key), value);
    }

    /**
     * 设置存储类型
     * @param type 存储类型
     * @return true表示切换成功，false表示切换被忽略
     * @throws IllegalStateException 数据库配置表无相关信息
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
            DiskConfig.setReadOnlyLevel(ReadOnlyLevel.DATA_MOVING);
            configDao.setConfigure(StoreType.getConfigKey(), type.toString());
            diskConfig.setStoreType(type.toString());
            storeTypeSwitch.switchTo(type);
            DiskConfig.setReadOnlyLevel(null);
        } catch (IOException | RuntimeException e) {
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
