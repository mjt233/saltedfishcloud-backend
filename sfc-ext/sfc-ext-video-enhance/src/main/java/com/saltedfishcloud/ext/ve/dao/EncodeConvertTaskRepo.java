package com.saltedfishcloud.ext.ve.dao;

import com.saltedfishcloud.ext.ve.model.po.EncodeConvertTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface EncodeConvertTaskRepo extends JpaRepository<EncodeConvertTask, Long> {
    EncodeConvertTask findByTaskId(String taskId);

    @Modifying
    @Query("UPDATE EncodeConvertTask SET taskStatus = ?2 WHERE taskId = ?1")
    void updateStatusByTaskId(String taskId, int status);
}
