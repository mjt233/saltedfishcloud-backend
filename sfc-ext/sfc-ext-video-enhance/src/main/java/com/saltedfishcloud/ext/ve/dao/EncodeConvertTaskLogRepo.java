package com.saltedfishcloud.ext.ve.dao;

import com.saltedfishcloud.ext.ve.model.po.EncodeConvertTaskLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EncodeConvertTaskLogRepo extends JpaRepository<EncodeConvertTaskLog, Long> {
    EncodeConvertTaskLog findByTaskId(Long taskId);
}
