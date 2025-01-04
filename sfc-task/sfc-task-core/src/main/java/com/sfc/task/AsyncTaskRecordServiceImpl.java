package com.sfc.task;

import com.sfc.task.model.AsyncTaskQueryParam;
import com.sfc.task.model.AsyncTaskRecord;
import com.sfc.task.repo.AsyncTaskRecordRepo;
import com.xiaotao.saltedfishcloud.model.CommonPageInfo;
import com.xiaotao.saltedfishcloud.model.po.User;
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
        return CommonPageInfo.of(asyncTaskRecordRepo.findAll((Specification<AsyncTaskRecord>) (root, query, criteriaBuilder) -> {
            List<Predicate> conditions = new ArrayList<>();
            if (param.getUid() != null) {
                conditions.add(criteriaBuilder.equal(root.get("uid"), param.getUid()));
            }
            if (param.getStatus() != null && !param.getStatus().isEmpty()) {
                CriteriaBuilder.In<Object> inStatus = criteriaBuilder.in(root.get("status"));
                param.getStatus().forEach(inStatus::value);
                conditions.add(inStatus);
            }
            return criteriaBuilder.and(conditions.toArray(new Predicate[0]));
        }, PageRequest.of(param.getPage(), param.getSize(), Sort.Direction.DESC, "id")));
    }
}
