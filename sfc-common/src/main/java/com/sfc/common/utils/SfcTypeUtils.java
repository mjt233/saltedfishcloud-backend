package com.sfc.common.utils;

import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Java数据类型相关处理工具类，包含数据类型boolean，number和string的识别与转换
 *
 * <p>由宿主 {@code com.xiaotao.saltedfishcloud.utils.TypeUtils} 迁移而来，方法体逐字保留，
 * 唯一的解耦点是 {@link #convert(Class, Object)} 里那个依赖宿主注解的分支：</p>
 *
 * <p><b>解耦方式（SPI）：</b>宿主原实现在 {@code convert} 的 else 分支里判断
 * 「{@code input} 是字符串且 {@code targetType} 标注了 {@code @ConfigPropertyEntity}」时，
 * 用 Jackson 把 JSON 字符串反序列化为该配置类对象。宿主注解 {@code ConfigPropertyEntity}
 * 位于 {@code sfc-api}，共享模块不能引用它（否则破坏「零宿主依赖」）。</p>
 *
 * <p>但该分支是<b>必需路径</b>：宿主的配置绑定
 * （{@code ConfigService.bindPropertyEntity} → {@code ObjectUtils.copyMapToBean}）与 MQ 反序列化
 * 都要靠它把库里的 JSON 字符串变成配置对象；删掉会直接导致
 * {@code 无法将 class java.lang.String 转为 ...Properties} 之类的启动失败。
 * 因此改为由宿主通过 {@link #setStringObjectConverter(SfcStringObjectConverter)} 注册实现，
 * 本类只负责在合适的时机调用它。</p>
 */
public class SfcTypeUtils {

    /**
     * 「字符串 → 复杂对象」扩展转换器（宿主注册）。
     *
     * <p>实现方应只在自身确实能处理该类型时返回转换结果，其余情况返回 {@code null}，
     * 表示"未处理"，由 {@link #convert(Class, Object)} 继续走内置转换逻辑。</p>
     */
    @FunctionalInterface
    public interface SfcStringObjectConverter {

        /**
         * 尝试把字符串转换为目标类型。
         *
         * @param input      源字符串
         * @param targetType 目标类型
         * @return 转换结果；无法处理时返回 {@code null}
         */
        Object convert(String input, Class<?> targetType);
    }

    /** 已注册的扩展转换器；未注册时为 null（此时行为等同"无该分支"，不影响内置转换） */
    private static volatile SfcStringObjectConverter stringObjectConverter;

    /**
     * 注册「字符串 → 复杂对象」扩展转换器。
     *
     * <p>宿主应在<b>普通 Bean 实例化之前</b>完成注册（例如通过 {@code BeanFactoryPostProcessor}）：
     * 该转换器在配置绑定型 Bean（如 {@code sysCommonConfig}）的创建过程中就会被用到，
     * 而普通 {@code @Bean} 之间没有可靠的创建顺序。</p>
     *
     * @param converter 转换器实现；传 {@code null} 可清除注册
     */
    public static void setStringObjectConverter(SfcStringObjectConverter converter) {
        stringObjectConverter = converter;
    }

    /**
     * 按 pattern 缓存的 DateTimeFormatter，线程安全，无额外包装
     */
    private static final Map<String, DateTimeFormatter> DATE_FORMAT_CACHE = new ConcurrentHashMap<>();

    private static final Map<Class<?>, String> NUMBER_TYPE = new HashMap<>();
    static {
        NUMBER_TYPE.put(Integer.class, "int");
        NUMBER_TYPE.put(Integer.TYPE, "int");
        NUMBER_TYPE.put(Long.class, "long");
        NUMBER_TYPE.put(Long.TYPE, "long");
        NUMBER_TYPE.put(Short.class, "short");
        NUMBER_TYPE.put(Short.TYPE, "short");
        NUMBER_TYPE.put(Double.class, "double");
        NUMBER_TYPE.put(Double.TYPE, "double");
        NUMBER_TYPE.put(Float.class, "float");
        NUMBER_TYPE.put(Float.TYPE, "float");
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
        if (type == null) {
            return false;
        }
        return Number.class.isAssignableFrom(type) ||
                Integer.TYPE.isAssignableFrom(type) ||
                Long.TYPE.isAssignableFrom(type) ||
                Double.TYPE.isAssignableFrom(type) ||
                Float.TYPE.isAssignableFrom(type) ||
                Short.TYPE.isAssignableFrom(type);
    }

    /**
     * 将输入对象转换为 Long。
     *
     * @param input 输入的数字、字符串或布尔值
     * @return 转换后的 Long，输入为 null 时返回 null
     */
    public static Long toLong(Object input) {
        return toNumber(Long.class, input);
    }

    /**
     * 将输入对象转换为 Integer。
     *
     * @param input 输入的数字、字符串或布尔值
     * @return 转换后的 Integer，输入为 null 时返回 null
     */
    public static Integer toInt(Object input) {
        return toNumber(Integer.class, input);
    }

    /**
     * 数字类型之间相互转换
     * @param target    目标简单数字类型
     * @param input     输入的数字类型
     * @return          转换后的类型
     */
    @SuppressWarnings("unchecked")
    public static <T> T toNumber(Class<T> target, Object input) {
        if (input == null) {
            return null;
        }
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
            if (inputStr.isEmpty()) {
                return null;
            }
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
        return type == boolean.class || Boolean.class.isAssignableFrom(type);
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
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> T convert(Class<T> targetType, Object input) {
        if (input == null) {
            return null;
        }
        if (targetType.isAssignableFrom(input.getClass())) {
            return (T)input;
        }
        if (isNumber(targetType)) {
            return toNumber(targetType, input);
        } else if (isString(targetType)) {
            return (T)input.toString();
        } else if (isBoolean(targetType)) {
            return (T)toBoolean(input);
        } else if (isEnum(targetType)) {
            return (T)Enum.valueOf((Class<? extends Enum>) targetType, input.toString());
        } else {
            // 先交给宿主注册的扩展转换器。
            // 宿主原实现在此处直接判断「字符串 + targetType 标注了 @ConfigPropertyEntity」后用
            // Jackson 反序列化；共享模块看不到宿主注解，故改为 SPI 回调，语义与位置保持一致：
            // 返回 null 或抛异常都视为"未处理"，继续走下面的内置转换。
            if (input instanceof String str) {
                SfcStringObjectConverter converter = stringObjectConverter;
                if (converter != null) {
                    try {
                        Object converted = converter.convert(str, targetType);
                        if (converted != null) {
                            return (T) converted;
                        }
                    } catch (Exception e) {
                        // 与宿主原实现一致：忽略失败并继续尝试其它转换方式
                    }
                }
            }
            // 尝试通过构造方法转换
            for (java.lang.reflect.Constructor<?> constructor : targetType.getDeclaredConstructors()) {
                if (constructor.getParameterCount() == 1) {
                    Class<?> paramType = constructor.getParameterTypes()[0];
                    if (paramType.isAssignableFrom(input.getClass()) || isSupportConvert(paramType)) {
                        try {
                            constructor.setAccessible(true);
                            return (T) constructor.newInstance(input);
                        } catch (Exception e) {
                            // 忽略，继续尝试其他构造方法
                        }
                    }
                }
            }
            throw new UnsupportedOperationException("无法将 " + input.getClass() + " 转为 " + targetType);
        }
    }

    /**
     * 判断类型是否为日期类型（{@link Date} 及其子类）。
     *
     * @param type 待判断的类型
     * @return 是日期类型时为 true
     */
    public static boolean isDate(Class<?> type) {
        return Date.class == type || Date.class.isAssignableFrom(type);
    }


    /**
     * 判断类型是否为枚举类型
     * @param type  待判断的类型
     * @return      枚举类型为true，否则为false
     */
    public static boolean isEnum(Class<?> type) {
        return Enum.class.isAssignableFrom(type);
    }

    /**
     * 判断类型是否支持直接转换
     * @param targetType
     * @return
     */
    public static boolean isSupportConvert(Class<?> targetType) {
        return isNumber(targetType) || isString(targetType) || isBoolean(targetType) || isEnum(targetType) || isDate(targetType);
    }

    /**
     * 将对象转为字符串（{@code null} 安全）。
     *
     * @param input 输入对象
     * @return 对象的 {@code toString()} 结果，输入为 null 时返回 null
     */
    public static String toString(Object input) {
        if (input == null) {
            return null;
        } else {
            return input.toString();
        }
    }

    /**
     * 获取指定 pattern 的 DateTimeFormatter（按 pattern 缓存）
     * @param pattern 日期格式模式
     * @return 缓存的 DateTimeFormatter 实例
     */
    private static DateTimeFormatter getFormatter(String pattern) {
        return DATE_FORMAT_CACHE.computeIfAbsent(pattern, DateTimeFormatter::ofPattern);
    }

    /**
     * 将字符串按指定日期模式解析为 Date
     * @param input   待解析的日期字符串
     * @param pattern 日期格式模式，如 "yyyy-MM-dd HH:mm:ss"
     * @return 解析后的 Date 对象，输入为空时返回 null
     */
    public static Date toDate(String input, String pattern) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        LocalDateTime ldt = LocalDateTime.parse(input, getFormatter(pattern));
        return Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
    }

    /**
     * 将 Date 按指定日期模式格式化为字符串
     * @param date    待格式化的 Date 对象
     * @param pattern 日期格式模式，如 "yyyy-MM-dd HH:mm:ss"
     * @return 格式化后的字符串，输入为空时返回 null
     */
    public static String dateToString(Date date, String pattern) {
        if (date == null) {
            return null;
        }
        LocalDateTime ldt = LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
        return getFormatter(pattern).format(ldt);
    }

    /**
     * 将 Date 按"yyyy-MM-dd HH:mm:ss"格式化为字符串
     * @param date    待格式化的 Date 对象
     * @return 格式化后的字符串，输入为空时返回 null
     */
    public static String dateToString(Date date) {
        return dateToString(date, "yyyy-MM-dd HH:mm:ss");
    }
}
