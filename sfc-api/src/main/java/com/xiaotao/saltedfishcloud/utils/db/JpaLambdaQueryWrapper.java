package com.xiaotao.saltedfishcloud.utils.db;

import com.xiaotao.saltedfishcloud.utils.ClassUtils;
import com.xiaotao.saltedfishcloud.utils.SFunc;
import com.xiaotao.saltedfishcloud.utils.TypeUtils;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.function.Consumer;

public class JpaLambdaQueryWrapper<T> {

    /**
     * 当前 QueryWrapper 已添加的条件
     */
    private final List<QueryCondition<T>> queryConditionList = new ArrayList<>();

    /**
     * 当前 QueryWrapper 已添加的排序字段
     */
    private final List<SortField> sortFieldList = new ArrayList<>();

    /**
     * 当前 QueryWrapper 所绑定的实体类class
     */
    private final Class<T> clazz;

    /**
     * 当前 QueryWrapper 的配置属性
     */
    private final QueryWrapperProperty property;

    public JpaLambdaQueryWrapper(Class<T> clazz, QueryWrapperProperty property) {
        this.clazz = clazz;
        this.property = property;
    }

    /**
     * 条件间的拼接逻辑，用于控制条件操作类型为 {@link Operate#SUB_CONDITION} 时，子条件间的拼接逻辑
     */
    public enum ConditionLogic {
        /**
         * 子条件使用 AND 连接
         */
        AND,

        /**
         * 子条件使用 OR 连接
         */
        OR
    }

    /**
     * 字段排序类型
     */
    public enum SortType {

        /**
         * 字段排序为正序（从小到大）
         */
        ASC,

        /**
         * 字段排序为倒序（从大到小）
         */
        DESC
    }

    /**
     * SQL对比操作符
     */
    public enum Operate {
        EQ, LT, GT, LE, GE, BT, IN, IS_NULL, NOT_NULL, LIKE,
        /**
         * 子条件
         */
        SUB_CONDITION
    }

    /**
     * 排序字段
     * @param field 字段 (Java实体类字段名)
     * @param type  排序类型
     */
    public record SortField(String field, SortType type) {}

    /**
     * 查询条件
     * @param field 条件字段
     * @param op    对比操作
     * @param conditionLogic 子条件见逻辑
     * @param val   对比值
     * @param subWrapper    子条件wrapper
     */
    public record QueryCondition<T>(String field, Operate op, ConditionLogic conditionLogic, Object val,  JpaLambdaQueryWrapper<T> subWrapper) {

        public Number getNumberVal() {
            return toNumberVal(val());
        }

        public Predicate toPredicate(Root<T> root, CriteriaBuilder cb) {
            Path<Object> objectPath = field() == null ? null : root.get(field());
            return switch (op()) {
                case EQ -> cb.equal(objectPath, val());
                case GE -> cb.gt(root.get(field()), getNumberVal());
                case GT -> cb.ge(root.get(field()), getNumberVal());
                case LT -> cb.lt(root.get(field()), getNumberVal());
                case LE -> cb.le(root.get(field()), getNumberVal());
                case IS_NULL -> cb.isNull(objectPath);
                case NOT_NULL -> cb.isNotNull(objectPath);
                case LIKE -> cb.like(root.get(field()), val().toString());
                case IN -> {
                    CriteriaBuilder.In<Object> in = cb.in(objectPath);
                    List<?> vals = (List<?>) val();
                    vals.forEach(val -> {
                        if (val instanceof Enum enumVal) {
                            in.value(enumVal.toString());
                        } else {
                            in.value(val);
                        }
                    });
                    yield in;
                }
                case BT -> {
                    List<?> vals = (List<?>) val();
                    Number val1 = toNumberVal(vals.get(0));
                    Number val2 = toNumberVal(vals.get(1));
                    if (val1 instanceof BigDecimal) {
                        yield cb.between(root.get(field()), (BigDecimal) val1,(BigDecimal) val2);
                    }
                    if (val1 instanceof BigInteger) {
                        yield cb.between(root.get(field()), (BigInteger) val1,(BigInteger) val2);
                    }
                    if (val1 instanceof Long) {
                        yield cb.between(root.get(field()), (Long) val1,(Long) val2);
                    }
                    if (val1 instanceof Integer) {
                        yield cb.between(root.get(field()), (Integer) val1,(Integer) val2);
                    }
                    if (val1 instanceof Short) {
                        yield cb.between(root.get(field()), (Short) val1,(Short) val2);
                    }
                    throw new IllegalArgumentException("Unexpected between value type: " + val1 + " " + val2);
                }
                case SUB_CONDITION -> {
                    if(conditionLogic() == ConditionLogic.OR) {
                        yield cb.or(subWrapper.toPredicate(root, cb));
                    } else {
                        yield cb.and(subWrapper.toPredicate(root, cb));
                    }
                }
            };
        }
    }

    private <R> String getFieldName(SFunc<T, R> func) {
        ClassUtils.LambdaMetaData metaData = ClassUtils.parseGetterLambdaMetaData(func);
        return metaData.fieldName();
    }

    private static Number toNumberVal(Object val) {
        if (val instanceof Number) {
            return (Number) val;
        }
        if (val instanceof Date) {
            return ((Date)val).getTime();
        }
        if (val instanceof String strVal) {
            if (strVal.contains(".")) {
                return TypeUtils.toNumber(BigDecimal.class, val);
            } else {
                return TypeUtils.toNumber(BigInteger.class, val);
            }
        }
        return TypeUtils.toInt(val);
    }

    private JpaLambdaQueryWrapper<T> handleNullStrategy(String field, Operate operate) {
        return switch (property.getNullStrategy()) {
            case TO_IS_NULL ->  this.isNull(field);
            case IGNORE -> this;
            case USE_DIRECT -> {
                queryConditionList.add(new QueryCondition<>(field, operate, ConditionLogic.AND, null, null));
                yield this;
            }
        };
    }

    private boolean isCanUseDirect(Object val) {
        return val != null || property.getNullStrategy() == NullStrategy.USE_DIRECT;
    }

    public <R> JpaLambdaQueryWrapper<T> eq(SFunc<T, R> func, Object val) {
        return this.eq(getFieldName(func), val);
    }

    public JpaLambdaQueryWrapper<T> eq(String field, Object val) {
        if (isCanUseDirect(val)) {
            queryConditionList.add(new QueryCondition<>(field, Operate.EQ, ConditionLogic.AND, val, null));
            return this;
        }
        return handleNullStrategy(field, Operate.EQ);
    }

    public JpaLambdaQueryWrapper<T> orderByAsc(String ...fields) {
        return this.orderBy(SortType.ASC, fields);
    }

    @SafeVarargs
    public final <R> JpaLambdaQueryWrapper<T> orderByAsc(SFunc<T, R>... fields) {
        for (SFunc<T, R> s : fields) {
            this.orderBy(SortType.ASC, getFieldName(s));
        }
        return this;
    }

    public JpaLambdaQueryWrapper<T> orderByDesc(String ...fields) {
        return this.orderBy(SortType.DESC, fields);
    }

    @SafeVarargs
    public final <R> JpaLambdaQueryWrapper<T> orderByDesc(SFunc<T, R>... fields) {
        for (SFunc<T, R> s : fields) {
            this.orderBy(SortType.DESC, getFieldName(s));
        }
        return this;
    }

    public  <R> JpaLambdaQueryWrapper<T> orderBy(SortType type, String ...field) {
        for (String s : field) {
            sortFieldList.add(new SortField(s, type));
        }
        return this;
    }

    public JpaLambdaQueryWrapper<T> and(Consumer<JpaLambdaQueryWrapper<T>> subWrapperConsumer) {
        JpaLambdaQueryWrapper<T> wrapper = new JpaLambdaQueryWrapper<>(clazz, property);
        subWrapperConsumer.accept(wrapper);
        queryConditionList.add(new QueryCondition<>(null, Operate.SUB_CONDITION, ConditionLogic.AND, null, wrapper));
        return this;
    }

    public JpaLambdaQueryWrapper<T> or(Consumer<JpaLambdaQueryWrapper<T>> subWrapperConsumer) {
        JpaLambdaQueryWrapper<T> wrapper = new JpaLambdaQueryWrapper<>(clazz, property);
        subWrapperConsumer.accept(wrapper);
        queryConditionList.add(new QueryCondition<>(null, Operate.SUB_CONDITION, ConditionLogic.OR, null, wrapper));
        return this;
    }

    public <R> JpaLambdaQueryWrapper<T> ge(SFunc<T, R> func, Object val) {
        return this.ge(getFieldName(func), val);
    }

    public JpaLambdaQueryWrapper<T> ge(String field, Object val) {
        if (isCanUseDirect(val)) {
            queryConditionList.add(new QueryCondition<>(field, Operate.GE, ConditionLogic.AND, val, null));
            return this;
        }
        return handleNullStrategy(field, Operate.GE);
    }

    public <R> JpaLambdaQueryWrapper<T> gt(SFunc<T, R> func, Object val) {
        return this.ge(getFieldName(func), val);
    }

    public JpaLambdaQueryWrapper<T> gt(String field, Object val) {
        if (isCanUseDirect(val)) {
            queryConditionList.add(new QueryCondition<>(field, Operate.GT, ConditionLogic.AND, val, null));
            return this;
        }
        return handleNullStrategy(field, Operate.GT);
    }

    public <R> JpaLambdaQueryWrapper<T> le(SFunc<T, R> func, Object val) {
        return this.le(getFieldName(func), val);
    }

    public JpaLambdaQueryWrapper<T> le(String field, Object val) {
        if (isCanUseDirect(val)) {
            queryConditionList.add(new QueryCondition<>(field, Operate.LE, ConditionLogic.AND, val, null));
            return this;
        }
        return handleNullStrategy(field, Operate.LE);
    }

    public <R> JpaLambdaQueryWrapper<T> lt(SFunc<T, R> func, Object val) {
        return this.le(getFieldName(func), val);
    }

    public JpaLambdaQueryWrapper<T> lt(String field, Object val) {
        if (isCanUseDirect(val)) {
            queryConditionList.add(new QueryCondition<>(field, Operate.LT, ConditionLogic.AND, val, null));
            return this;
        }
        return handleNullStrategy(field, Operate.LT);
    }

    public <R> JpaLambdaQueryWrapper<T> isNull(SFunc<T, R> func) {
        return this.isNull(getFieldName(func));
    }

    public JpaLambdaQueryWrapper<T> isNull(String field) {
        queryConditionList.add(new QueryCondition<>(field, Operate.IS_NULL, ConditionLogic.AND, null, null));
        return this;
    }

    public <R> JpaLambdaQueryWrapper<T> isNotNull(SFunc<T, R> func) {
        return this.isNotNull(getFieldName(func));
    }

    public JpaLambdaQueryWrapper<T> isNotNull(String field) {
        queryConditionList.add(new QueryCondition<>(field, Operate.NOT_NULL, ConditionLogic.AND, null, null));
        return this;
    }

    public <R> JpaLambdaQueryWrapper<T> like(SFunc<T, R> func, String val) {
        return this.like(getFieldName(func), val);
    }

    public JpaLambdaQueryWrapper<T> like(String field, String val) {
        if (isCanUseDirect(val)) {
            queryConditionList.add(new QueryCondition<>(field, Operate.LIKE, ConditionLogic.AND, val, null));
            return this;
        }
        return handleNullStrategy(field, Operate.LIKE);
    }

    public <R> JpaLambdaQueryWrapper<T> between(SFunc<T, R> func, Object val1, Object val2) {
        return this.between(getFieldName(func), val1, val2);
    }

    public JpaLambdaQueryWrapper<T> between(String field, Object val1, Object val2) {
        queryConditionList.add(new QueryCondition<>(field, Operate.BT, ConditionLogic.AND, List.of(val1, val2), null));
        return this;
    }

    public <R> JpaLambdaQueryWrapper<T> in(SFunc<T, R> func, Object...val) {
        return this.in(getFieldName(func), val);
    }

    public JpaLambdaQueryWrapper<T> in(String field, Object...val) {
        if (property.isInEmptyIgnore() && (val == null || val.length == 0)) {
            return this;
        }
        if (property.isInNullToIsNull()) {
            List<Object> inValList = Arrays.stream(val).filter(Objects::nonNull).toList();
            if (inValList.size() != val.length) {
                return this.or(orWrapper -> orWrapper.isNull(field)
                        .in(field, inValList));
            }
        }

        queryConditionList.add(new QueryCondition<>(field, Operate.IN, ConditionLogic.AND, Arrays.stream(val).toList(), null));
        return this;
    }

    public <R> JpaLambdaQueryWrapper<T> in(SFunc<T, R> func, Collection<?> val) {
        return this.in(getFieldName(func), val);
    }

    public JpaLambdaQueryWrapper<T> in(String field, Collection<?> val) {
        if (property.isInEmptyIgnore() && (val == null || val.isEmpty())) {
            return this;
        }
        if (property.isInNullToIsNull()) {
            List<?> inValList = val.stream().filter(Objects::nonNull).toList();
            if (inValList.size() != val.size()) {
                return this.or(orWrapper -> orWrapper.isNull(field)
                        .in(field, inValList));
            }
        }
        queryConditionList.add(new QueryCondition<>(field, Operate.IN, ConditionLogic.AND, val, null));
        return this;
    }

    public static <T> JpaLambdaQueryWrapper<T> get(Class<T> clazz) {
        return new JpaLambdaQueryWrapper<>(clazz, QueryWrapperProperty.builder().build());
    }

    public static <T> JpaLambdaQueryWrapper<T> get(Class<T> clazz, QueryWrapperProperty property) {
        return new JpaLambdaQueryWrapper<>(clazz, property);
    }

    public Predicate[] toPredicate(Root<T> root, CriteriaBuilder cb) {
        return queryConditionList.stream().map(c -> c.toPredicate(root, cb))
                .toArray(Predicate[]::new);
    }

    public Specification<T> build() {
        return (root, query, cb) -> {
            List<Predicate> jpaConditions = new ArrayList<>();
            for (QueryCondition<T> queryCondition : queryConditionList) {
                jpaConditions.add(queryCondition.toPredicate(root, cb));
            }

            List<Order> orderList = sortFieldList.stream().map(f -> {
                if (f.type() == SortType.ASC) {
                    return cb.asc(root.get(f.field()));
                } else {
                    return cb.desc(root.get(f.field()));
                }
            }).toList();

            if (query != null) {
                query.where(jpaConditions.toArray(new Predicate[0]));
                if (!sortFieldList.isEmpty()) {
                    query.orderBy(orderList);
                }
                return query.getRestriction();
            } else {
                return cb.and(jpaConditions.toArray(new Predicate[0]));
            }

        };
    }
}
