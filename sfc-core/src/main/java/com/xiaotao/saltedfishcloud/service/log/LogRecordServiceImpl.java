package com.xiaotao.saltedfishcloud.service.log;

import com.xiaotao.saltedfishcloud.dao.jpa.LogRecordRepo;
import com.xiaotao.saltedfishcloud.model.po.LogRecord;
import com.xiaotao.saltedfishcloud.service.CrudServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


@Service
@Slf4j
public class LogRecordServiceImpl extends CrudServiceImpl<LogRecord, LogRecordRepo> implements LogRecordService  {

    @Override
    public void saveRecord(LogRecord logRecord) {
        save(logRecord);
    }
}
