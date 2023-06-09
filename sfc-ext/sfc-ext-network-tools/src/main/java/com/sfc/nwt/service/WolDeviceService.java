package com.sfc.nwt.service;

import com.sfc.common.service.CrudServiceImpl;
import com.sfc.nwt.model.po.WolDevice;
import com.sfc.nwt.repo.WolDeviceRepo;
import com.sfc.nwt.utils.NetworkUtils;
import com.xiaotao.saltedfishcloud.model.template.AuditModel;
import com.xiaotao.saltedfishcloud.validator.UIDValidator;

import java.io.IOException;
import java.util.Collection;
import java.util.Date;


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
     * 唤醒设备
     * @param id    WOL设备id
     */
    public void wake(Long id) throws IOException {
        WolDevice device = findById(id);
        UIDValidator.validate(device.getUid(), false);

        NetworkUtils.wakeOnLan(device.getMac(), device.getPort());
        device.setLastWakeAt(new Date());
        save(device);
    }
}
