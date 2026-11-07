package com.xiaotao.saltedfishcloud.utils;

import com.sfc.common.model.SfcPageInfo;
import com.sfc.common.model.json.SfcJsonResult;
import org.springframework.data.domain.Page;

public class PageUtils {
    public static <T> SfcJsonResult<SfcPageInfo<T>> getInstanceWithPage(Page<T> page) {
        return SfcJsonResult.getInstance(SfcPageInfo.of(page));
    }
}
