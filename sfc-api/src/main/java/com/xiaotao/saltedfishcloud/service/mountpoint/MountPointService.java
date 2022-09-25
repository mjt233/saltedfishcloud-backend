package com.xiaotao.saltedfishcloud.service.mountpoint;

import com.xiaotao.saltedfishcloud.model.po.MountPoint;

import java.util.List;
import java.util.Map;

public interface MountPointService {
    /**
     * 根据用户id查找挂载点
     * @param uid   用户id
     */
    List<MountPoint> findByUid(long uid);

    /**
     * 添加挂载点
     * @param mountPoint    挂载点信息
     */
    void addMountPoint(MountPoint mountPoint);

    /**
     * 查找用户的所有挂载点的路径
     * @param uid   用户id
     * @return key - 挂载点路径，value - 挂载点信息
     */
    Map<String, MountPoint> findMountPointPathByUid(long uid);

}
