package com.xiaotao.saltedfishcloud.dao.jpa;

import com.xiaotao.saltedfishcloud.dao.BaseRepo;
import com.xiaotao.saltedfishcloud.model.po.MountPoint;
import com.xiaotao.saltedfishcloud.service.BaseJpaService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

public interface MountPointRepo extends BaseRepo<MountPoint> {
    /**
     * 根据用户id查询所有挂载点
     * @param uid   用户id
     */
    List<MountPoint> findByUid(long uid);

    /**
     * 根据挂载点id批量删除
     * @param ids   id集合
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM MountPoint mp WHERE mp.id IN (?1)")
    void batchDeleteById(Collection<Long> ids);
}
