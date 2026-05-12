package com.xiaotao.saltedfishcloud.dao.jpa;

import com.xiaotao.saltedfishcloud.model.po.CollectionInfo;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

public interface CollectionInfoRepo extends JpaRepository<CollectionInfo, Long> {
    /**
     * 将截止到指定时间已过期的收集任务状态更新为关闭。
     *
     * @param expiredAt 当前比较时间
     * @return 更新的记录数
     */
    @Transactional
    @Query("UPDATE CollectionInfo C SET C.state = 'CLOSED' WHERE C.expiredAt <= :expiredAt")
    @Modifying
    int updateState(@Param("expiredAt") Date expiredAt);

    List<CollectionInfo> findByUidEquals(Long uid, Sort sort);

    @Modifying
    @Transactional
    @Query("UPDATE CollectionInfo C SET C.available = C.available - 1 WHERE C.id = ?1 AND C.available = ?2")
    int consumeCount(Long cid, Integer oldCount);
}
