package com.xiaotao.saltedfishcloud.service.download;

public interface ProgressExtractor {
    long getTotal();
    long getLoaded();
    long getSpeed();
}
