package com.xiaotao.saltedfishcloud.utils.compress.reader;

import java.io.IOException;
import java.io.InputStream;

public interface InputStreamFactory {
    InputStream getInputStream() throws IOException;
}
