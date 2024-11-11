package com.xiaotao.saltedfishcloud.constant;

/**
 * 需要拒绝的表达式
 */
public interface RejectRegex {
    /**
     * 不合法的文件名
     */
    String FILE_NAME = "[\\\\/:*\"<>|?\\r\\n]|(^\\.{1,2}$)";

    /**
     * 不合法的路径
     */
    String PATH = "(\\\\|/)\\.\\.(\\\\|/)|(\\\\|/)\\.\\.$|^\\.\\.(\\\\|/)";
}
