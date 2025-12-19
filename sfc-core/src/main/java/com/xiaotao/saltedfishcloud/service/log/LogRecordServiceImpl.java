package com.xiaotao.saltedfishcloud.service.log;

import com.xiaotao.saltedfishcloud.dao.jpa.LogRecordRepo;
import com.xiaotao.saltedfishcloud.model.po.LogRecord;
import com.xiaotao.saltedfishcloud.service.CrudServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class LogRecordServiceImpl extends CrudServiceImpl<LogRecord, LogRecordRepo> implements LogRecordService{
}
