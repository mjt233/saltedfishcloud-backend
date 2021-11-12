package com.xiaotao.saltedfishcloud.dao.jpa;

import com.xiaotao.saltedfishcloud.entity.po.CollectionRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CollectionRecordRepo extends JpaRepository<CollectionRecord, Integer> {
    @Query(nativeQuery = true,
            countProjection = "1",
            value =
            "SELECT cid, uid, c.id, filename, size, md5, c.created_at , c.ip , u.user AS username " +
            "FROM collection_rec AS c LEFT JOIN user AS u ON c.uid = u.id " +
            "WHERE cid = ?1 " +
            "ORDER BY c.created_at DESC")
    Page<CollectionRecord> findByCid(Long cid, PageRequest page);
}
