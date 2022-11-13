package com.xiaotao.saltedfishcloud.service.mountpoint;

import com.xiaotao.saltedfishcloud.exception.FileSystemParameterException;
import com.xiaotao.saltedfishcloud.model.po.MountPoint;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface MountPointService {

    /**
     * 根据id查询挂载点
     * @param id    挂载点id
     */
    MountPoint findById(long id);

    /**
     * 根据用户id查找挂载点
     * @param uid   用户id
     */
    List<MountPoint> findByUid(long uid);

    List<MountPoint> listAll();

    /**
     * 添加/修改挂载点
     * @param mountPoint    挂载点信息
     */
    void saveMountPoint(MountPoint mountPoint) throws IOException, FileSystemParameterException;

    /**
     * 移除挂载点
     * @param uid   挂载点的用户id
     * @param id    挂载点id
     */
    void remove(long uid, long id);

    /**
     * 批量移除挂载点
     * @param uid   挂载点的用户id
     * @param ids    挂载点id
     */
    void batchRemove(long uid, Collection<Long> ids);

    /**
     * 查找用户的所有挂载点的路径
     * @param uid   用户id
     * @return key - 挂载点所处目录的路径，value - 挂载点信息
     */
    Map<String, MountPoint> findMountPointPathByUid(long uid);
}
