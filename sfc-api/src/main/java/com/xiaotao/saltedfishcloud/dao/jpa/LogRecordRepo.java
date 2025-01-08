package com.xiaotao.saltedfishcloud.dao.jpa;

import com.xiaotao.saltedfishcloud.dao.BaseRepo;
import com.xiaotao.saltedfishcloud.model.po.LogRecord;
import com.xiaotao.saltedfishcloud.model.vo.LogRecordStatisticVO;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Date;
import java.util.List;

public interface LogRecordRepo extends BaseRepo<LogRecord> {
    @Query("""
        SELECT
            new com.xiaotao.saltedfishcloud.model.vo.LogRecordStatisticVO(
                r.type,
                COUNT(r)
            )
        FROM
            LogRecord r
        WHERE
            r.createAt >= :begin
            AND r.createAt <= :end
        GROUP BY
            r.type
    """)
    List<LogRecordStatisticVO> queryStatistic(@Param("begin") Date begin,@Param("end") Date end);


}
