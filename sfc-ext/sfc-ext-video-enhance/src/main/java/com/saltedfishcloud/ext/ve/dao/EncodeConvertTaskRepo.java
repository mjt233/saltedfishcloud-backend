package com.saltedfishcloud.ext.ve.dao;

import com.saltedfishcloud.ext.ve.model.po.EncodeConvertTask;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EncodeConvertTaskRepo extends JpaRepository<EncodeConvertTask, Long> {
    EncodeConvertTask findByTaskId(String taskId);

    Page<EncodeConvertTask> findByUidAndTaskStatusOrderByCreateAtDesc(Long uid, Integer status, Pageable pageable);
}
