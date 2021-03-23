package com.xiaotao.saltedfishcloud.utils;

import java.util.HashSet;
import java.util.Set;

public class SetUtils {
    /**
     * 获取集合a和b中的差集（a有而b没有的集合）
     * @param a 集合A
     * @param b 集合B
     * @return （a有 但b没有的集合）
     */
    public static <T> Set<T> diff (Set<T> a, Set<T> b) {
        Set<T> res = new HashSet<>();
        a.forEach(e -> {
            if ( !b.contains(e)) {
                res.add(e);
            }
        });
        return res;
    }
}
