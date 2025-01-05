package com.xiaotao.saltedfishcloud.service.log;

import com.xiaotao.saltedfishcloud.dao.jpa.LogRecordRepo;
import com.xiaotao.saltedfishcloud.model.CommonPageInfo;
import com.xiaotao.saltedfishcloud.model.param.LogRecordQueryParam;
import com.xiaotao.saltedfishcloud.model.param.PageableRequest;
import com.xiaotao.saltedfishcloud.model.po.LogRecord;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
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
        return CommonPageInfo.of(
                // 封装一个类似Mybatis-Plus的查询构造器工具
                logRecordRepo.findAll(
                        (Specification<LogRecord>) (root, query, cb) -> {
                            List<Predicate> conditions = new ArrayList<>();
                            Optional.ofNullable(queryParam.getUid())
                                    .ifPresent(uid -> conditions.add(cb.equal(root.get("uid"), uid)));

                            Optional.ofNullable(queryParam.getHost())
                                    .ifPresent(host -> conditions.add(cb.equal(root.get("host"), host)));

                            Optional.ofNullable(queryParam.getPid())
                                    .ifPresent(pid -> conditions.add(cb.equal(root.get("pid"), pid)));

                            Optional.ofNullable(queryParam.getType())
                                    .ifPresent(type -> conditions.add(cb.equal(root.get("type"), type)));

                            Optional.ofNullable(queryParam.getLevel())
                                    .filter(enumList -> !enumList.isEmpty())
                                    .ifPresent(enumList -> {
                                        CriteriaBuilder.In<Object> levelCondition = cb.in(root.get("level"));
                                        enumList.stream().map(e -> e.getLevel().toString()).forEach(levelCondition::value);
                                        conditions.add(levelCondition);
                                    });

                            Optional.ofNullable(queryParam.getDateRange())
                                    .ifPresent(range -> {
                                        Optional.ofNullable(range.begin())
                                                .ifPresent(begin -> conditions.add(cb.ge(root.get("createAt"), begin.getTime())));
                                        Optional.ofNullable(range.end())
                                                .ifPresent(end -> conditions.add(cb.le(root.get("createAt"), end.getTime())));
                                    });

                            return cb.and(conditions.toArray(new Predicate[0]));
                        },
                        PageRequest.of(
                                Optional.ofNullable(pageableRequest.getPage()).orElse(0),
                                Optional.ofNullable(pageableRequest.getSize()).orElse(50),
                                Sort.by(Sort.Direction.DESC, "createAt")
                        )
                )
        );
    }

    @Override
    public String getName() {
        return "Database";
    }
}
