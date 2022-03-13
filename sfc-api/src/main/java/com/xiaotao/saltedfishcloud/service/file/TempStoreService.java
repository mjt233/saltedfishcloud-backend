package com.xiaotao.saltedfishcloud.service.file;

import com.xiaotao.saltedfishcloud.service.file.store.DirectRawStoreHandler;

import java.io.IOException;

/**
 * 在目标存储服务上以临时文件目录为根目录，提供临时文件操作功能。
 */
public interface TempStoreService extends DirectRawStoreHandler {

    /**
     * 清空临时目录
     */
    void clean() throws IOException;
}
