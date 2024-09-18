package com.xiaotao.saltedfishcloud.model.po;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.xiaotao.saltedfishcloud.model.dto.CollectionDTO;
import com.xiaotao.saltedfishcloud.service.collection.CollectionParser;
import com.sfc.constant.ByteSize;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.util.StringUtils;

import javax.persistence.*;
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
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "CHAR(32) COMMENT '校验码'")
    private String verification;

    @Column(columnDefinition = "BIGINT UNSIGNED COMMENT '创建者ID'", nullable = false)
    private Long uid;

    @Column(columnDefinition = "VARCHAR(128) COMMENT '接收者署名'", nullable = false)
    private String nickname;

    @Column(name = "`describe`", columnDefinition = "TEXT COMMENT '收集任务描述'")
    private String describe;

    @Column(columnDefinition = "VARCHAR(128) COMMENT '标题'", nullable = false)
    private String title;

    @Column(name = "max_size", columnDefinition = "BIGINT COMMENT '允许的文件最大大小（Byte），-1为无限制'", nullable = false)
    private Long maxSize = ByteSize._1MiB * 128L;

    @Column(name = "allow_anonymous",columnDefinition = "tinyint(1) COMMENT '是否允许匿名上传'", nullable = false)
    private Boolean allowAnonymous = true;

    @Column(columnDefinition = "INT COMMENT '允许的最大收集文件数量，-1为无限制'", nullable = false)
    private Integer allowMax = 100;

    @Column(columnDefinition = "INT COMMENT '该收集可用容量（还可以接受的文件数）'", nullable = false)
    private Integer available = 100;

    @Column(columnDefinition = "VARCHAR(1024) COMMENT '文件名匹配表达式，可以是正则或字段拼接'")
    private String pattern;

    @Column(name = "ext_pattern",columnDefinition = "VARCHAR(1024) COMMENT '允许的文件后缀名正则表达式，被测试的后缀名不带.'")
    private String extPattern;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(columnDefinition = "VARCHAR(1024) COMMENT 'JSON类型数组，每个元素应包含name - 字段名称，pattern - 匹配正则，describe - 字段描述，type - 类型'")
    private String field;

    @Column(name = "save_node",columnDefinition = "CHAR(32) COMMENT '收集到文件后保存到的网盘数据节点'", nullable = false)
    private String saveNode;

    @Column(name = "save_path_snapshot", columnDefinition = "VARCHAR(1024) COMMENT '收集到文件后保存到的网盘位置快照（仅记录创建时的设定）'" ,nullable = false)
    private String savePathSnapshot;

    @Column(name = "expired_at", columnDefinition = "DATETIME COMMENT '任务过期时间'", nullable = false)
    private Date expiredAt;

    @CreatedDate
    @Column(name = "created_at", columnDefinition = "DATETIME COMMENT '任务过期时间'", nullable = false)
    private Date createdAt;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "enum('OPEN','CLOSED') COMMENT '状态，开放或关闭'", nullable = false)
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
