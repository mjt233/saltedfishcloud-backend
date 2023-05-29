package com.xiaotao.saltedfishcloud.service.collection;

import com.sfc.constant.error.CollectionError;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.exception.JsonException;

public class CollectionCheckedException extends JsonException {
    public CollectionCheckedException(String extra) {
        super(JsonResultImpl.getInstance(
                CollectionError.COLLECTION_CHECK_FAILED.getStatus(),
                null,
                CollectionError.COLLECTION_CHECK_FAILED.getMessage() + ":" + extra)
        );
    }
}
