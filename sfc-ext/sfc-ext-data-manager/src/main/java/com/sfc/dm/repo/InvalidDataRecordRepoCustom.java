package com.sfc.dm.repo;

import com.sfc.dm.model.po.InvalidDataRecord;
import org.springframework.data.jpa.domain.Specification;

import java.util.stream.Stream;

/**
 * 失效数据记录 Repository 自定义方法接口
 */
public interface InvalidDataRecordRepoCustom {
    /**
     * 流式查询所有满足条件的记录（配合 {@link org.hibernate.jpa.AvailableHints#HINT_FETCH_SIZE} 避免一次加载全部数据到内存）。
     *
     * @param spec 查询条件（{@link com.xiaotao.saltedfishcloud.utils.db.JpaLambdaQueryWrapper#build()} 创建）
     * @return 记录流，使用后需关闭
     */
    Stream<InvalidDataRecord> streamAll(Specification<InvalidDataRecord> spec);
}
