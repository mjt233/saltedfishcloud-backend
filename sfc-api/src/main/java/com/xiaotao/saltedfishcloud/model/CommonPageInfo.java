package com.xiaotao.saltedfishcloud.model;

import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.data.domain.Page;

import java.util.List;

@Data
@Accessors(chain = true)
public class CommonPageInfo<T> {
    private List<T> content;
    private long totalCount;
    private long totalPage;

    public static <T> CommonPageInfo<T> of(Page<T> page) {
        return new CommonPageInfo<T>()
                .setContent(page.getContent())
                .setTotalCount(page.getTotalElements())
                .setTotalPage(page.getTotalPages());
    }
}
