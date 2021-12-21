package com.xiaotao.saltedfishcloud.service.collection;

import com.xiaotao.saltedfishcloud.entity.ErrorInfo;
import com.xiaotao.saltedfishcloud.entity.po.JsonResult;
import com.xiaotao.saltedfishcloud.exception.JsonException;

public class CollectionCheckedException extends JsonException {
    public CollectionCheckedException(String extra) {
        super(JsonResult.getInstance(
                ErrorInfo.COLLECTION_CHECK_FAILED.getStatus(),
                null,
                ErrorInfo.COLLECTION_CHECK_FAILED.getMessage() + ":" + extra)
        );
    }
}
