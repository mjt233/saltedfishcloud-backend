package com.sfc.nwt.service;

import com.sfc.cloud.data.service.SfcCrudServiceImpl;
import com.sfc.nwt.model.po.WolDevice;
import com.sfc.nwt.repo.WolDeviceRepo;
import com.sfc.nwt.utils.NetworkUtils;
import com.sfc.cloud.data.model.SfcAuditModel;
import com.sfc.common.utils.SfcStringUtils;
import com.sfc.cloud.data.validation.SfcUIDValidator;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;


public class WolDeviceService extends SfcCrudServiceImpl<WolDevice, WolDeviceRepo> {
    @Override
    public void save(WolDevice entity) {
        SfcUIDValidator.validate(entity.getUid(), true);
        super.save(entity);
    }

    @Override
    public void batchSave(Collection<WolDevice> entityList) {
        entityList.stream().map(SfcAuditModel::getUid).distinct().forEach(uid -> SfcUIDValidator.validate(uid, true));
        super.batchSave(entityList);
    }

    /**
     * 查询用户的WOL设备列表并检测是否在线
     * @param uid   用户id
     * @return      设备列表
     */
    public List<WolDevice> findByUidAndCheckOnline(Long uid) {
        List<WolDevice> deviceList = findByUid(uid);
        Set<String> ips = deviceList.stream().map(WolDevice::getIp).filter(SfcStringUtils::hasText).collect(Collectors.toSet());
        Set<String> onlineIps = NetworkUtils.testIpAlive(ips);
        for (WolDevice wolDevice : deviceList) {
            wolDevice.setIsOnline(onlineIps.contains(wolDevice.getIp()));
        }
        return deviceList;
    }

    /**
     * 唤醒设备
     * @param id    WOL设备id
     */
    public void wake(Long id) throws IOException {
        WolDevice device = findById(id);
        SfcUIDValidator.validate(device.getUid(), false);

        NetworkUtils.wakeOnLan(
                device.getMac(),
                Optional.ofNullable(device.getSendIp()).orElse("255.255.255.255"),
                device.getPort()
        );
        device.setLastWakeAt(new Date());
        save(device);
    }

    public int batchDelete(Collection<Long> ids) {
        List<WolDevice> deviceList = repository.findAllById(ids);
        for (WolDevice wolDevice : deviceList) {
            SfcUIDValidator.validate(wolDevice.getUid(), true);
        }
        return repository.batchDelete(ids);
    }
}
