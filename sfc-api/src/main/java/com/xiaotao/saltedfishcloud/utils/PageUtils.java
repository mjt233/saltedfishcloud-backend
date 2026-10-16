package com.xiaotao.saltedfishcloud.utils;

import com.sfc.cloud.model.SfcPageInfo;
import com.sfc.cloud.model.json.SfcJsonResult;
import org.springframework.data.domain.Page;

public class PageUtils {
    public static <T> SfcJsonResult<SfcPageInfo<T>> getInstanceWithPage(Page<T> page) {
        return SfcJsonResult.getInstance(SfcPageInfo.of(page));
    }
}
