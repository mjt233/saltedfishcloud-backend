package com.xiaotao.saltedfishcloud.compress.reader;

import java.io.IOException;
import java.io.InputStream;

public interface InputStreamFactory {
    InputStream getInputStream() throws IOException;
}
