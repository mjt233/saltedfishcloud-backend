package com.xiaotao.saltedfishcloud.utils;

import com.github.pagehelper.PageInfo;
import com.xiaotao.saltedfishcloud.model.CommonPageInfo;
import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import org.springframework.data.domain.Page;

public class PageUtils {
    public static <T> JsonResult<CommonPageInfo<T>> getInstanceWithPage(Page<T> page) {
        return JsonResultImpl.getInstance(CommonPageInfo.of(page));
    }

    public static <T> JsonResult<CommonPageInfo<T>> getInstanceWithPage(PageInfo<T> page) {
        return JsonResultImpl.getInstance(CommonPageInfo.of(page));
    }
}
