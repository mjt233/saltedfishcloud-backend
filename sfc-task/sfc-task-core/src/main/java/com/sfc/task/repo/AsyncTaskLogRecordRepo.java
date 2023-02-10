package com.sfc.task.repo;

import com.sfc.task.model.AsyncTaskLogRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AsyncTaskLogRecordRepo extends JpaRepository<AsyncTaskLogRecord, Long> {
    AsyncTaskLogRecord findFirstByTaskIdOrderByIdDesc(Long taskId);
}
