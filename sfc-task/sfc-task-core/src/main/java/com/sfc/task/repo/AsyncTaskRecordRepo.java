package com.sfc.task.repo;

import com.sfc.task.model.AsyncTaskRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AsyncTaskRecordRepo extends JpaRepository<AsyncTaskRecord, Long> {
}
