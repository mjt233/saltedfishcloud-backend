package com.xiaotao.saltedfishcloud.service.breakpoint.merge;

import java.io.IOException;
import java.io.InputStream;

public interface InputStreamGenerator {
    /**
     * 获取下一个InputStream，如果不可再次获取应返回null
     */
    InputStream next() throws IOException;

    /**
     * 如果存在可用的新InputStream，返回true
     */
    boolean hasNext();
}
