package com.xiaotao.saltedfishcloud.entity.po;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.xiaotao.saltedfishcloud.entity.dto.CollectionDTO;
import com.xiaotao.saltedfishcloud.service.collection.CollectionParser;
import com.xiaotao.saltedfishcloud.utils.ByteSize;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.GenericGenerator;
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
@Table(name = "collection")
@NoArgsConstructor
public class CollectionInfo {
    public enum State {
        OPEN, CLOSED
    }
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String verification;
    private Integer uid;
    private String nickname;

    @Column(name = "`describe`")
    private String describe;
    private String title;
    private Long maxSize = ByteSize._1MiB * 128L;
    private Boolean allowAnonymous = true;
    private Integer allowMax = 100;
    private String pattern;
    private String extPattern;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String field;
    private String saveNode;
    private Date expiredAt;
    private Integer available = 100;

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

    @CreatedDate
    private Date createdAt;

    @Enumerated(EnumType.STRING)
    private State state;

    public CollectionInfo(Integer uid,String nickname, String title, String describe, String saveNode, Date expiredAt, String extPattern) {
        this.uid = uid;
        this.title = title;
        this.describe = describe;
        this.saveNode = saveNode;
        this.expiredAt = expiredAt;
        this.nickname = nickname;
        this.state = State.OPEN;
        this.extPattern = extPattern;
    }

    public CollectionInfo(Integer uid, CollectionDTO info) {
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
