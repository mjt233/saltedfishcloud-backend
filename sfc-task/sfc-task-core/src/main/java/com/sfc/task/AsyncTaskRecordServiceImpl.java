package com.sfc.task;

import com.sfc.task.model.AsyncTaskQueryParam;
import com.sfc.task.model.AsyncTaskRecord;
import com.sfc.task.repo.AsyncTaskRecordRepo;
import com.xiaotao.saltedfishcloud.model.CommonPageInfo;
import com.xiaotao.saltedfishcloud.model.po.User;
import com.xiaotao.saltedfishcloud.utils.db.JpaLambdaQueryWrapper;
import com.xiaotao.saltedfishcloud.validator.UIDValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class AsyncTaskRecordServiceImpl implements AsyncTaskRecordService {
    @Autowired
    private AsyncTaskRecordRepo asyncTaskRecordRepo;

    @Override
    public CommonPageInfo<AsyncTaskRecord> listRecord(AsyncTaskQueryParam param) {
        UIDValidator.validate(Optional.ofNullable(param.getUid()).orElse(User.PUBLIC_USER_ID), true);

        return CommonPageInfo.of(asyncTaskRecordRepo.findAll(JpaLambdaQueryWrapper.get(AsyncTaskRecord.class)
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
