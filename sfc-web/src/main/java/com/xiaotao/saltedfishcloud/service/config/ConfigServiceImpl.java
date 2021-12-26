package com.xiaotao.saltedfishcloud.service.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.config.StoreType;
import com.xiaotao.saltedfishcloud.dao.mybatis.ConfigDao;
import com.xiaotao.saltedfishcloud.entity.po.ConfigInfo;
import com.xiaotao.saltedfishcloud.enums.ReadOnlyLevel;
import com.xiaotao.saltedfishcloud.service.mail.MailProperties;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import javafx.util.Pair;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ConfigServiceImpl implements ConfigService {
    @Resource
    private ConfigDao configDao;
    @Resource
    private StoreTypeSwitch storeTypeSwitch;
    @Resource
    private DiskConfig diskConfig;
    @Resource
    private MailProperties mailProperties;
    private final ArrayList<Consumer<Pair<ConfigName, String>>> listeners = new ArrayList<>();

    @Override
    public void addConfigChangeListener(Consumer<Pair<ConfigName, String>> listener) {
        listeners.add(listener);
    }

    /**
     * 获取存在的所有配置
     */
    @Override
    public Map<ConfigName, String> getAllConfig() {
        return configDao
                .getAllConfig()
                .stream()
                .collect(
                        Collectors.toMap(
                                ConfigInfo::getKey,
                                ConfigInfo::getValue
                        )
                );
    }

    /**
     * 从配置表读取一个配置项的值
     * @param key   配置名
     * @return      结果
     */
    @Override
    public String getConfig(ConfigName key) {
        return configDao.getConfigure(key);
    }


    public boolean setMailProperties(MailProperties properties) {
        boolean res = false;
        try {
            res = setConfig(ConfigName.MAIL_PROPERTIES, MapperHolder.mapper.writeValueAsString(properties));
            BeanUtils.copyProperties(properties, mailProperties);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return res;
    }

    public MailProperties getMailProperties() {
        String config = getConfig(ConfigName.MAIL_PROPERTIES);
        if (config == null) {
            return null;
        } else {
            try {
                return MapperHolder.mapper.readValue(config, MailProperties.class);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    /**
     * 设置一个配置项
     * @param key       配置项
     * @param value     配置值
     */
    @Override
    public boolean setConfig(ConfigName key, String value) throws IOException {
        switch (key) {
            case STORE_TYPE: return setStoreType(StoreType.valueOf(value));
            case REG_CODE: setInviteRegCode(value); return true;
            case SYNC_DELAY:
                DiskConfig.SYNC_DELAY = Integer.parseInt(value);
                configDao.setConfigure(key, value);
            default:
                configDao.setConfigure(key, value);
        }
        for (Consumer<Pair<ConfigName, String>> listener : listeners) {
            listener.accept(new Pair<>(key, value));
        }
        return true;
    }
    /**
     * 设置一个配置项
     * @param key       配置项
     * @param value     配置值
     */
    @Override
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
