package com.xiaotao.saltedfishcloud.utils;

import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

/**
 * Java数据类型相关处理工具类，包含数据类型boolean，number和string的识别与转换
 */
public class TypeUtils {

    private static final Map<Class<?>, String> NUMBER_TYPE = new HashMap<>();
    static {
        NUMBER_TYPE.put(Integer.class, "int");
        NUMBER_TYPE.put(Long.class, "long");
        NUMBER_TYPE.put(Short.class, "short");
        NUMBER_TYPE.put(Double.class, "double");
        NUMBER_TYPE.put(Float.class, "float");
        NUMBER_TYPE.put(BigDecimal.class, "bigDecimal");
        NUMBER_TYPE.put(BigInteger.class, "bigInteger");
    }


    /**
     * 获取数字类型的类型名称简称字符串
     * @param type  待判断的类型
     * @return int、long、short、double、float、bigDecimal、bigInteger
     */
    public static String getNumberName(Class<?> type) {
        return NUMBER_TYPE.get(type);
    }

    /**
     * 判断类型是否为数字类型
     * @param type  待判断的类型
     * @return      数字类型为true，否则为false
     */
    public static boolean isNumber(Class<?> type) {
        return Number.class.isAssignableFrom(type);
    }

    /**
     * 数字类型之间相互转换
     * @param target    目标简单数字类型
     * @param input     输入的数字类型
     * @return          转换后的类型
     */
    @SuppressWarnings("unchecked")
    public static <T> T toNumber(Class<T> target, Object input) {
        // 相同类型直接返回
        if (target.isAssignableFrom(input.getClass())) {
            return (T)input;
        }

        // boolean直接对应0和1转换
        if (input instanceof Boolean) {
            return toNumber(target, (Boolean)input ? 1 : 0);
        }

        // 其他转BigInteger，直接toString后舍弃小数点
        if (BigInteger.class.isAssignableFrom(target)) {
            String inputStr = input.toString();
            final int pos = inputStr.indexOf('.');
            if (pos != -1) {
                inputStr = inputStr.substring(0, pos);
            }
            return (T)new BigInteger(inputStr);
        }

        // 其他转BigDecimal，直接toString
        if (BigDecimal.class.isAssignableFrom(target)) {
            return (T)new BigDecimal(input.toString());
        }

        if (input instanceof String) {

            // 对于输入的字符串，以是否存在“.“作为浮点数判断依据，转换时先转换为BigDecimal再进行目标转换
            String inputStr = (String) input;
            if (inputStr.contains(".")) {
                return toNumber(target, toNumber(BigDecimal.class, input));
            } else {
                return toNumber(target, toNumber(BigInteger.class, input));
            }
        } else if (input instanceof Number) {

            //  对于输入的数字，直接调用对应的"目标类型Value()"方法
            String typeName = getNumberName(target);
            try {
                return (T)input.getClass().getDeclaredMethod(typeName + "Value").invoke(input);
            } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                e.printStackTrace();
                throw new IllegalArgumentException("无法将" + input.getClass() + " 转换为 " + target);
            }
        } else {
            throw new IllegalArgumentException(input.getClass() + " 不是数字（Number）类型");
        }
    }

    /**
     * 判断待检测类型是否为boolean
     * @param type  待检测类型
     * @return      是boolean为true，否则false
     */
    public static boolean isBoolean(Class<?> type) {
        return Boolean.class.isAssignableFrom(type);
    }

    /**
     * 判断待检测类型是否为String
     * @param type  待检测类型
     * @return      是boolean为true，否则false
     */
    public static boolean isString(Class<?> type) {
        return String.class.isAssignableFrom(type);
    }

    /**
     * 将数字，字符串转为boolean
     * @param obj   字符串或数字类型
     * @return      boolean转换结果
     */
    public static Boolean toBoolean(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj instanceof String) {
            return ((String) obj).equalsIgnoreCase("true");
        } else if (obj instanceof Number) {
            return ((Number) obj).intValue() >= 1;
        } else if (obj instanceof Boolean) {
            return (Boolean) obj;
        } else {
            throw new UnsupportedOperationException("无法将 " + obj.getClass() + " 转为boolean");
        }
    }

    /**
     * 判断类型是否为数字，boolean或字符串类型
     * @param type  待判断的类型
     * @return      是则true，否则false
     */
    public static boolean isSimpleType(Class<?> type) {
        return Number.class.isAssignableFrom(type) ||
                String.class.isAssignableFrom(type) ||
                Boolean.class.isAssignableFrom(type);
    }

    /**
     * 将输入的类型转换为目标类型
     * @param targetType    目标输出类型
     * @param input         输入的数据
     * @return              转换后的数据对象
     */
    @SuppressWarnings("unchecked")
    public static <T> T convert(Class<T> targetType, Object input) {
        if (targetType.isAssignableFrom(input.getClass())) {
            return (T)input;
        }
        if (isNumber(targetType)) {
            return toNumber(targetType, input);
        } else if (isString(targetType)) {
            return (T)input.toString();
        } else if (isBoolean(targetType)) {
            return (T)toBoolean(input);
        } else {
            throw new UnsupportedOperationException("无法将 " + input.getClass() + " 转为 " + targetType);
        }
    }

    public static String toString(Object input) {
        if (input == null) {
            return null;
        } else {
            return input.toString();
        }
    }
}
