package com.xiaotao.saltedfishcloud.dao.jpa;

import com.xiaotao.saltedfishcloud.entity.po.CollectionInfo;
import com.xiaotao.saltedfishcloud.entity.po.CollectionInfoId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface CollectionInfoRepository extends JpaRepository<CollectionInfo, Long> {
    List<CollectionInfo> findByUidEquals(Integer uid);

    @Modifying
    @Transactional
    @Query("UPDATE CollectionInfo C SET C.available = C.available - 1 WHERE C.id = ?1 AND C.available = ?2")
    int consumeCount(Long cid, Integer oldCount);
}
