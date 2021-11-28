package com.xiaotao.saltedfishcloud.compress.filesystem;

import java.io.IOException;
import java.io.InputStream;

public interface InputStreamFactory {
    InputStream getInputStream() throws IOException;
}
