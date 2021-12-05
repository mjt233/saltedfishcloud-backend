package com.xiaotao.saltedfishcloud.validator;

public class RejectRegex {
    public final static String FILE_NAME = "[\\\\/:*\"<>|?\\r\\n]|(^\\.{1,2}$)";
    public final static String PATH = "(\\\\|/)\\.\\.(\\\\|/)|(\\\\|/)\\.\\.$|^\\.\\.(\\\\|/)";
}
