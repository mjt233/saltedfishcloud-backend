package com.xiaotao.saltedfishcloud.service.log;

import com.xiaotao.saltedfishcloud.dao.jpa.LogRecordRepo;
import com.xiaotao.saltedfishcloud.model.po.LogRecord;
import com.sfc.cloud.data.service.SfcCrudServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class LogRecordServiceImpl extends SfcCrudServiceImpl<LogRecord, LogRecordRepo> implements LogRecordService{
}
