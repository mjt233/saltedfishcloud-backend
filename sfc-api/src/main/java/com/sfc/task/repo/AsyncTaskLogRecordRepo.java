package com.sfc.task.repo;

import com.sfc.task.model.AsyncTaskLogRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AsyncTaskLogRecordRepo extends JpaRepository<AsyncTaskLogRecord, Long> {
    AsyncTaskLogRecord findFirstByTaskIdOrderByIdDesc(Long taskId);

    List<AsyncTaskLogRecord> findByTaskId(Long taskId);
}
