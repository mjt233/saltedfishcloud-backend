package com.xiaotao.saltedfishcloud.dao.jpa;

import com.xiaotao.saltedfishcloud.model.dto.CollectionRecordDTO;
import com.xiaotao.saltedfishcloud.model.po.CollectionRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CollectionRecordRepo extends JpaRepository<CollectionRecord, Long> {
    @Query("""
    SELECT new com.xiaotao.saltedfishcloud.model.dto.CollectionRecordDTO(
        c.id, c.cid, c.uid, c.filename, c.size, c.md5, c.ip, u.user AS username, c.createdAt
            )
            FROM CollectionRecord AS c LEFT JOIN user AS u ON c.uid = u.id
            WHERE c.cid = :cid
            ORDER BY c.createdAt DESC
    """)
    Page<CollectionRecordDTO> findByCid(@Param("cid") Long cid, Pageable page);
}
