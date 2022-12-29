package com.saltedfishcloud.ext.ve.dao;

import com.saltedfishcloud.ext.ve.model.po.EncodeConvertTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface EncodeConvertTaskRepo extends JpaRepository<EncodeConvertTask, Long> {
    EncodeConvertTask findByTaskId(String taskId);

    @Modifying
    @Query("UPDATE EncodeConvertTask SET taskStatus = :status WHERE taskId = :taskId")
    @Transactional
    void updateStatusByTaskId(@Param("taskId") String taskId,@Param("status") int status);
}
