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

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Predicate;
import java.util.Optional;

@Component
public class AsyncTaskRecordServiceImpl implements AsyncTaskRecordService {
    @Autowired
    private AsyncTaskRecordRepo asyncTaskRecordRepo;

    @Override
    public CommonPageInfo<AsyncTaskRecord> listRecord(AsyncTaskQueryParam param) {
        UIDValidator.validate(Optional.ofNullable(param.getUid()).orElse((long)User.PUBLIC_USER_ID), true);
        return CommonPageInfo.of(asyncTaskRecordRepo.findAll((Specification<AsyncTaskRecord>) (root, query, criteriaBuilder) -> {
            Predicate condition = criteriaBuilder.conjunction();
            if (param.getUid() != null) {
                criteriaBuilder.and(condition, criteriaBuilder.equal(root.get("uid"), param.getUid()));
            }
            if (param.getStatus() != null && !param.getStatus().isEmpty()) {
                CriteriaBuilder.In<Object> inStatus = criteriaBuilder.in(root.get("status"));
                param.getStatus().forEach(inStatus::value);
                criteriaBuilder.and(condition, inStatus);
            }
            return query.where(condition).getRestriction();
        }, PageRequest.of(param.getPage(), param.getSize(), Sort.Direction.DESC, "id")));
    }
}
