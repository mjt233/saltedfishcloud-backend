package com.sfc.task;

import com.sfc.task.model.AsyncTaskQueryParam;
import com.sfc.task.model.AsyncTaskRecord;
import com.sfc.task.repo.AsyncTaskRecordRepo;
import com.sfc.common.constant.SfcUserConstants;
import com.sfc.common.model.SfcPageInfo;
import com.xiaotao.saltedfishcloud.utils.db.JpaLambdaQueryWrapper;
import com.sfc.cloud.data.validation.SfcUIDValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class AsyncTaskRecordServiceImpl implements AsyncTaskRecordService {
    @Autowired
    private AsyncTaskRecordRepo asyncTaskRecordRepo;

    @Override
    public SfcPageInfo<AsyncTaskRecord> listRecord(AsyncTaskQueryParam param) {
        SfcUIDValidator.validate(Optional.ofNullable(param.getUid()).orElse(SfcUserConstants.PUBLIC_USER_ID), true);

        return SfcPageInfo.of(asyncTaskRecordRepo.findAll(JpaLambdaQueryWrapper.get(AsyncTaskRecord.class)
                                .eq(AsyncTaskRecord::getUid, param.getUid())
                                .in(AsyncTaskRecord::getStatus, param.getStatus())
                        .build(),
                PageRequest.of(
                        param.getPage(),
                        param.getSize(),
                        Sort.Direction.DESC,
                        "id"
                ))
        );
    }
}
