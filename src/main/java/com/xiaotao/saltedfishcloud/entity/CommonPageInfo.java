package com.xiaotao.saltedfishcloud.entity;

import com.github.pagehelper.PageInfo;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.data.domain.Page;

@Data
@Accessors(chain = true)
public class CommonPageInfo {
    private Object content;
    private long totalCount;
    private long totalPage;

    public static CommonPageInfo of(Page<?> page) {
        return new CommonPageInfo()
                .setContent(page.getContent())
                .setTotalCount(page.getTotalElements())
                .setTotalPage(page.getTotalPages());
    }

    public static CommonPageInfo of(PageInfo<?> page) {
        return new CommonPageInfo()
                .setContent(page.getList())
                .setTotalPage(page.getPageNum())
                .setTotalCount(page.getSize());
    }
}
