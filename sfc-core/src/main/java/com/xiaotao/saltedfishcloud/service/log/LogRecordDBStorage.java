package com.xiaotao.saltedfishcloud.service.log;

import com.xiaotao.saltedfishcloud.dao.jpa.LogRecordRepo;
import com.xiaotao.saltedfishcloud.model.CommonPageInfo;
import com.xiaotao.saltedfishcloud.model.param.LogRecordQueryParam;
import com.xiaotao.saltedfishcloud.model.param.PageableRequest;
import com.xiaotao.saltedfishcloud.model.param.RangeRequest;
import com.xiaotao.saltedfishcloud.model.po.LogRecord;
import com.xiaotao.saltedfishcloud.utils.db.JpaLambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Optional;


@Service
@Slf4j
public class LogRecordDBStorage extends AbstractLogRecordStorage implements LogRecordStorage {
    @Autowired
    private LogRecordService logRecordService;

    @Autowired
    private LogRecordRepo logRecordRepo;

    @Override
    protected void doSaveRecord(LogRecord logRecord) {
        logRecordService.save(logRecord);
    }

    @Override
    public CommonPageInfo<LogRecord> query(LogRecordQueryParam queryParam) {
        PageableRequest pageableRequest = Optional.ofNullable(queryParam.getPageableRequest()).orElseGet(PageableRequest::new);
        return CommonPageInfo.of(logRecordRepo.findAll(
                JpaLambdaQueryWrapper.get(LogRecord.class)
                        .eq(LogRecord::getUid, queryParam.getUid())
                        .eq(LogRecord::getProducerHost, queryParam.getHost())
                        .eq(LogRecord::getProducerPid, queryParam.getPid())
                        .eq(LogRecord::getType, queryParam.getType())
                        .in(LogRecord::getLevel, queryParam.getLevel())
                        .ge(LogRecord::getCreateAt, Optional.ofNullable(queryParam.getDateRange()).map(RangeRequest::begin).orElse(null))
                        .le(LogRecord::getCreateAt, Optional.ofNullable(queryParam.getDateRange()).map(RangeRequest::end).orElse(null))
                        .build(),
                PageRequest.of(
                        Optional.ofNullable(pageableRequest.getPage()).orElse(0),
                        Optional.ofNullable(pageableRequest.getSize()).orElse(50),
                        Sort.by(Sort.Direction.DESC, "createAt")
                )
        ));
    }

    @Override
    public String getName() {
        return "Database";
    }
}
