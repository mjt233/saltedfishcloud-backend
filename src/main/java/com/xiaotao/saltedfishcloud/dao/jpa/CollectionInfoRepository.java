package com.xiaotao.saltedfishcloud.dao.jpa;

import com.xiaotao.saltedfishcloud.entity.po.CollectionInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CollectionInfoRepository extends JpaRepository<CollectionInfo, String> {
    List<CollectionInfo> findByUidEquals(Integer uid);

    @Modifying
    @Query("UPDATE CollectionInfo C SET C.available = C.available - 1 WHERE C.id = ?1 AND C.available = ?2")
    int consumeCount(String cid, Integer oldCount);
}
