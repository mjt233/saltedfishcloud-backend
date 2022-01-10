package com.xiaotao.saltedfishcloud.utils;

import com.github.pagehelper.PageInfo;
import com.xiaotao.saltedfishcloud.entity.CommonPageInfo;
import com.xiaotao.saltedfishcloud.entity.JsonResult;
import com.xiaotao.saltedfishcloud.entity.JsonResultImpl;
import org.springframework.data.domain.Page;

public class PageUtils {
    public static <T> JsonResult getInstanceWithPage(Page<T> page) {
        return JsonResultImpl.getInstance(CommonPageInfo.of(page));
    }

    public static <T> JsonResult getInstanceWithPage(PageInfo<T> page) {
        return JsonResultImpl.getInstance(CommonPageInfo.of(page));
    }
}
