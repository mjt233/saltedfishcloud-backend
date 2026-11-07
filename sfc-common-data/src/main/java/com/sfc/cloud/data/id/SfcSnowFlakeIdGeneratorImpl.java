package com.sfc.cloud.data.id;

import com.sfc.cloud.data.model.SfcBaseModel;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.IdentifierGenerator;

import java.io.Serializable;

/**
 * Hibernate 雪花id主键生成器。
 *
 * <p>由注解 {@link SfcSnowFlakeIdGenerator} 通过 Hibernate 的
 * {@code @IdGeneratorType} 机制实例化。</p>
 *
 * <p><b>与宿主实现的差异</b>：仅把判定的实体基类由宿主的 {@code BaseModel}
 * 换成共享基座的 {@link SfcBaseModel}，id 的取值逻辑保持逐字不变。</p>
 *
 * @see SfcSnowFlakeIdGenerator
 * @see SfcIdUtil#getId()
 */
public class SfcSnowFlakeIdGeneratorImpl implements IdentifierGenerator {

    /**
     * 生成实体主键值。
     *
     * <p>取值规则：</p>
     * <ul>
     *   <li>被持久化对象是 {@link SfcBaseModel} 且其 id 为 {@code null} 时，生成新的雪花id；</li>
     *   <li>被持久化对象是 {@link SfcBaseModel} 但已携带 id 时，直接沿用该 id（支持显式指定主键）；</li>
     *   <li>其它类型对象一律生成新的雪花id。</li>
     * </ul>
     *
     * @param sharedSessionContractImplementor Hibernate 当前会话（本实现不使用）
     * @param o                                待生成主键的实体对象
     * @return 主键值
     */
    @Override
    public Serializable generate(SharedSessionContractImplementor sharedSessionContractImplementor, Object o) {
        if (o instanceof SfcBaseModel model) {
            if (model.getId() == null) {
                return SfcIdUtil.getId();
            } else {
                return model.getId();
            }
        }
        return SfcIdUtil.getId();
    }
}
