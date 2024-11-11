package com.xiaotao.saltedfishcloud.service.mountpoint;

import com.xiaotao.saltedfishcloud.exception.FileSystemParameterException;
import com.xiaotao.saltedfishcloud.model.param.MountPointSyncFileRecordParam;
import com.xiaotao.saltedfishcloud.model.po.MountPoint;
import com.xiaotao.saltedfishcloud.service.CrudService;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface MountPointService extends CrudService<MountPoint> {

    /**
     * 根据用户id查找挂载点
     * @param uid   用户id
     */
    List<MountPoint> findByUid(long uid);

    /**
     * 列出系统中所有的挂载点
     */
    List<MountPoint> listAll();

    /**
     * 列出路径下的所有挂载点
     * @param uid   用户id
     * @param path  待检查的路径
     * @return      挂载点对象中会包含path属性
     */
    List<MountPoint> listByPath(long uid, String path);

    /**
     * 清空用户的挂载点缓存
     * @param uid   用户id
     */
    void clearCache(long uid);

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

    /**
     * 同步挂载点的文件信息到文件记录服务
     * @param id    挂载点id
     */
    void syncFileRecord(MountPointSyncFileRecordParam param) throws IOException, FileSystemParameterException;
}
