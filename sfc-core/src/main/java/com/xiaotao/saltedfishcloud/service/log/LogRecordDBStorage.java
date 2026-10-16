package com.xiaotao.saltedfishcloud.service.log;

import com.xiaotao.saltedfishcloud.dao.jpa.LogRecordRepo;
import com.sfc.cloud.model.SfcPageInfo;
import com.xiaotao.saltedfishcloud.model.param.LogRecordQueryParam;
import com.sfc.cloud.model.param.SfcPageableRequest;
import com.xiaotao.saltedfishcloud.model.param.RangeRequest;
import com.xiaotao.saltedfishcloud.model.po.LogRecord;
import com.xiaotao.saltedfishcloud.model.vo.LogRecordStatisticVO;
import com.xiaotao.saltedfishcloud.utils.db.JpaLambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Optional;


@Service
@Slf4j
public class LogRecordDBStorage extends AbstractLogRecordStorage {
    @Autowired
    private LogRecordService logRecordService;

    @Autowired
    private LogRecordRepo logRecordRepo;

    @Override
    protected void doSaveRecord(LogRecord logRecord) {
        logRecordService.save(logRecord);
    }

    @Override
    public SfcPageInfo<LogRecord> query(LogRecordQueryParam queryParam) {
        SfcPageableRequest pageableRequest = Optional.ofNullable(queryParam.getPageableRequest()).orElseGet(SfcPageableRequest::new);
        return SfcPageInfo.of(logRecordRepo.findAll(
                JpaLambdaQueryWrapper.get(LogRecord.class)
                        .eq(LogRecord::getUid, queryParam.getUid())
                        .eq(LogRecord::getProducerHost, queryParam.getHost())
                        .eq(LogRecord::getProducerPid, queryParam.getPid())
                        .in(LogRecord::getType, queryParam.getType())
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
    public List<LogRecordStatisticVO> queryStatistic(RangeRequest<Date> request) {
        if (request != null && request.begin() != null && request.end() != null) {
            return logRecordRepo.queryStatistic(request.begin(), request.end());
        } else {
            return logRecordRepo.queryStatistic();
        }
    }

    @Override
    public String getName() {
        return "Database";
    }
}
