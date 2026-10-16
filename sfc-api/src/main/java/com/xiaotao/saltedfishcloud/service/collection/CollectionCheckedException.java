package com.xiaotao.saltedfishcloud.service.collection;

import com.xiaotao.saltedfishcloud.constant.error.CollectionError;
import com.sfc.cloud.model.json.SfcJsonResult;
import com.sfc.cloud.exception.SfcJsonException;

public class CollectionCheckedException extends SfcJsonException {
    public CollectionCheckedException(String extra) {
        super(SfcJsonResult.getInstance(
                CollectionError.COLLECTION_CHECK_FAILED.getStatus(),
                null,
                CollectionError.COLLECTION_CHECK_FAILED.getMessage() + ":" + extra)
        );
    }
}
