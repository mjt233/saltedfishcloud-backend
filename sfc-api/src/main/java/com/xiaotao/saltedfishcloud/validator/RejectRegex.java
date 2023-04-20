package com.xiaotao.saltedfishcloud.validator;

public interface RejectRegex {
    String FILE_NAME = "[\\\\/:*\"<>|?\\r\\n]|(^\\.{1,2}$)";
    String PATH = "(\\\\|/)\\.\\.(\\\\|/)|(\\\\|/)\\.\\.$|^\\.\\.(\\\\|/)";
}
