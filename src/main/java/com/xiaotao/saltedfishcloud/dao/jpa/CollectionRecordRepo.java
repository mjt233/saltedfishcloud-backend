package com.xiaotao.saltedfishcloud.dao.jpa;

import com.xiaotao.saltedfishcloud.entity.po.CollectionRecord;
import com.xiaotao.saltedfishcloud.entity.po.CollectionRecordId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectionRecordRepo extends JpaRepository<CollectionRecord, CollectionRecordId> {
    int countByIdCid(String cid);
}
