package com.xiaotao.saltedfishcloud.dao.jpa;

import com.xiaotao.saltedfishcloud.model.po.MountPoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MountPointRepo extends JpaRepository<MountPoint, Long> {
    /**
     * 根据用户id查询所有挂载点
     * @param uid   用户id
     */
    List<MountPoint> findByUid(long uid);
}
