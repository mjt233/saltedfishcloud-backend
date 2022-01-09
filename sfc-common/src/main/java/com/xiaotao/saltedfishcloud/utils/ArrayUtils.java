package com.xiaotao.saltedfishcloud.utils;

public class ArrayUtils {
    /**
     * 遍历数组
     * @param arr
     * @param v
     * @return
     */
    public static boolean contain(Object[] arr, Object v) {
        for (Object o : arr) {
            if (o.equals(v)) return true;
        }
        return false;
    }
}
