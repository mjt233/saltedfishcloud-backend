package com.xiaotao.saltedfishcloud.entity.po;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.xiaotao.saltedfishcloud.entity.dto.CollectionDTO;
import com.xiaotao.saltedfishcloud.utils.ByteSize;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import javax.persistence.*;
import java.util.Collection;
import java.util.Date;

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
    private String id;
    private Integer uid;
    private String nickname;

    @Column(name = "`describe`")
    private String describe;
    private String title;
    private Long maxSize = ByteSize._1MiB * 128L;
    private Boolean allowAnonymous = true;
    private Integer allowMax = 100;
    private String pattern;
    private String field;
    private String saveNode;
    private Date expiredAt;

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

    @CreatedDate
    private Date createdAt;

    @Enumerated(EnumType.STRING)
    private State state;

    public CollectionInfo(Integer uid,String nickname, String title, String describe, String saveNode, Date expiredAt) {
        this.uid = uid;
        this.title = title;
        this.describe = describe;
        this.saveNode = saveNode;
        this.expiredAt = expiredAt;
        this.nickname = nickname;
        this.state = State.OPEN;
    }

    public CollectionInfo(Integer uid, CollectionDTO info) {
        this(uid, info.getNickname(), info.getTitle(), info.getDescribe(), info.getSaveNode(), info.getExpiredAt());
        this.allowAnonymous = info.getAllowAnonymous();
        this.maxSize = info.getMaxSize();
        this.pattern = info.getPattern();
        this.allowMax = info.getAllowMax();
        if (info.getField() != null) {
            setField(info.getField());
        }
    }
}
