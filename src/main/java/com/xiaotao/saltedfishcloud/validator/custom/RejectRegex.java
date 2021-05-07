package com.xiaotao.saltedfishcloud.validator.custom;

public class RejectRegex {
    public final static String FILE_NAME = "[\\\\/:*\"<>|?]|(^\\.{1,2}$)";
    public final static String PATH = "(\\\\|/)\\.\\.(\\\\|/)|(\\\\|/)\\.\\.$|^\\.\\.(\\\\|/)";
}
