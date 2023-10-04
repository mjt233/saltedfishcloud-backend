package com.xiaotao.saltedfishcloud.utils;

import lombok.experimental.UtilityClass;

import java.util.Calendar;
import java.util.Date;

@UtilityClass
public class DateUtils {

    public static String format(long timestamp) {
        return format(new Date(timestamp));
    }

    public static String format(Date date) {
        if (date == null) {
            return null;
        }
        Calendar instance = Calendar.getInstance();
        instance.setTime(date);
        StringBuilder sb = new StringBuilder();
        sb.append(instance.get(Calendar.YEAR))
                .append("-");
        appendWithZeroPrefix(sb, instance.get(Calendar.MONTH)).append("-");
        appendWithZeroPrefix(sb, instance.get(Calendar.DAY_OF_MONTH)).append(" ");
        appendWithZeroPrefix(sb, instance.get(Calendar.HOUR_OF_DAY)).append(":");
        appendWithZeroPrefix(sb, instance.get(Calendar.MINUTE)).append(":");
        appendWithZeroPrefix(sb, instance.get(Calendar.MILLISECOND));
        return sb.toString();
    }

    private static StringBuilder appendWithZeroPrefix(StringBuilder sb, int num) {
        if (num < 10) {
            sb.append("0");
        }
        if (num > 100 && num < 1000) {
            num = num / 10;
        }
        sb.append(num);
        return sb;
    }
}
