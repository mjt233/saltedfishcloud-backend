package com.xiaotao.saltedfishcloud.utils;

public class ArrayUtils {
    /**
     * 遍历数组判断是否包含某对象
     * @param arr   数组
     * @param v     要查找的对象
     * @return      存在true，不存在false
     */
    public static boolean contain(Object[] arr, Object v) {
        for (Object o : arr) {
            if (o.equals(v)) return true;
        }
        return false;
    }
}
