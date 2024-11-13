package com.xiaotao.saltedfishcloud.model.po;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.xiaotao.saltedfishcloud.annotations.SnowFlakeId;
import com.xiaotao.saltedfishcloud.constant.ByteSize;
import com.xiaotao.saltedfishcloud.model.dto.CollectionDTO;
import com.xiaotao.saltedfishcloud.service.collection.CollectionParser;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Date;
import java.util.List;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Data
@Accessors(chain = true)
@Table(
        name = "`collection`",
        indexes = {
                @Index(name = "uid_index", columnList = "uid"),
                @Index(name = "expired_index", columnList = "expired_at")
        }
)
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CollectionInfo {
    public enum State {
        OPEN, CLOSED
    }
    @Id
    @SnowFlakeId
    private Long id;

    /**
     * 校验码
     */
    @Column(length = 32)
    private String verification;

    @Column(nullable = false)
    private Long uid;

    /**
     * 接收者署名
     */
    @Column(length = 128, nullable = false)
    private String nickname;

    /**
     * 收集任务描述
     */
    @Lob
    @Column(name = "`describe`")
    private String describe;

    /**
     * 标题
     */
    @Column(length = 128, nullable = false)
    private String title;

    /**
     * 允许的文件最大大小（Byte），-1为无限制
     */
    @Column(nullable = false)
    private Long maxSize = ByteSize._1MiB * 128L;

    /**
     * 是否允许匿名上传
     */
    @Column(nullable = false)
    private Boolean allowAnonymous = true;

    /**
     * 允许的最大收集文件数量，-1为无限制
     */
    @Column(nullable = false)
    private Integer allowMax = 100;

    /**
     * 该收集可用容量（还可以接受的文件数）
     */
    @Column(nullable = false)
    private Integer available = 100;

    /**
     * 文件名匹配表达式，可以是正则或字段拼接
     */
    @Column(length = 1024)
    private String pattern;

    /**
     * 允许的文件后缀名正则表达式，被测试的后缀名不带.
     */
    @Column(length = 1024)
    private String extPattern;

    /**
     * JSON类型数组，每个元素应包含name - 字段名称，pattern - 匹配正则，describe - 字段描述，type - 类型
     * @see CollectionField
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(length = 1024)
    private String field;

    /**
     * 收集到文件后保存到的网盘数据节点
     */
    @Column(nullable = false)
    private String saveNode;

    /**
     * 收集到文件后保存到的网盘位置快照（仅记录创建时的设定）
     */
    @Column(length = 1024,nullable = false)
    private String savePathSnapshot;

    /**
     * 任务过期时间
     */
    @Column(nullable = false)
    private Date expiredAt;

    /**
     * 任务过期时间
     */
    @CreatedDate
    @Column(nullable = false)
    private Date createdAt;

    /**
     * 状态，开放或关闭
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private State state;

    public CollectionInfo setField(String field) {
        this.field = field;
        return this;
    }

    public CollectionInfo setField(Collection<CollectionField> field) {
        try {
            this.field = MapperHolder.mapper.writeValueAsString(field);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            this.field = null;
        }
        return this;
    }

    /**
     * 获取字段信息列表，若字段列表长度为0或为null，则统一返回null
     * @return  字段信息列表
     */
    @JsonProperty("field")
    public List<CollectionField> getFields() {
        if (StringUtils.hasLength(field)) {
            try {
                List<CollectionField> res = MapperHolder.mapper.readValue(field, CollectionParser.FIELD_LIST_TYPE_REFERENCE);
                if (res.size() == 0) {
                    return null;
                } else {
                    return res;
                }
            } catch (JsonProcessingException e) {
                e.printStackTrace();
                return null;
            }
        } else {
            return null;
        }
    }

    public CollectionInfo(Long uid,String nickname, String title, String describe, String saveNode, Date expiredAt, String extPattern) {
        this.uid = uid;
        this.title = title;
        this.describe = describe;
        this.saveNode = saveNode;
        this.expiredAt = expiredAt;
        this.nickname = nickname;
        this.state = State.OPEN;
        this.extPattern = extPattern;
    }

    public CollectionInfo(Long uid, CollectionDTO info) {
        this(uid, info.getNickname(), info.getTitle(), info.getDescribe(), info.getSaveNode(), info.getExpiredAt(), info.getExtPattern());
        this.allowAnonymous = info.getAllowAnonymous();
        this.maxSize = info.getMaxSize();
        this.pattern = info.getPattern();
        this.allowMax = info.getAllowMax();
        this.available = info.getAllowMax();
        if (info.getField() != null) {
            setField(info.getField());
        }
    }
}
