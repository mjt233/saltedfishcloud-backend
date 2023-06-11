package com.sfc.nwt.service;

import com.sfc.common.service.CrudServiceImpl;
import com.sfc.nwt.model.po.WolDevice;
import com.sfc.nwt.repo.WolDeviceRepo;
import com.sfc.nwt.utils.NetworkUtils;
import com.xiaotao.saltedfishcloud.model.template.AuditModel;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import com.xiaotao.saltedfishcloud.validator.UIDValidator;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;


public class WolDeviceService extends CrudServiceImpl<WolDevice, WolDeviceRepo> {
    @Override
    public void save(WolDevice entity) {
        UIDValidator.validate(entity.getUid(), true);
        super.save(entity);
    }

    @Override
    public void batchSave(Collection<WolDevice> entityList) {
        entityList.stream().map(AuditModel::getUid).distinct().forEach(uid -> UIDValidator.validate(uid, true));
        super.batchSave(entityList);
    }

    /**
     * 查询用户的WOL设备列表并检测是否在线
     * @param uid   用户id
     * @return      设备列表
     */
    public List<WolDevice> findByUidAndCheckOnline(Long uid) {
        List<WolDevice> deviceList = findByUid(uid);
        Set<String> ips = deviceList.stream().map(WolDevice::getIp).filter(StringUtils::hasText).collect(Collectors.toSet());
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
        UIDValidator.validate(device.getUid(), false);

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
            UIDValidator.validate(wolDevice.getUid(), true);
        }
        return repository.batchDelete(ids);
    }
}
