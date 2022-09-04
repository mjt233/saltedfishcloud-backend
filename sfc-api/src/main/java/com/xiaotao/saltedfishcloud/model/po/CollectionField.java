package com.xiaotao.saltedfishcloud.model.po;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

@Data
@Accessors(chain = true)
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CollectionField {
    public enum Type {
        TEXT, OPTION
    }

    private String name;
    private Type type;
    private String value;
    private String describe;
    private String pattern;
    private List<String> options;

    public CollectionField(String name, Type type) {
        this.name = name;
        this.type = type;
    }

    public CollectionField addOption(String option) {
        if (options == null) {
            options = new ArrayList<>();
        }
        options.add(option);
        return this;
    }

    @Override
    public String toString() {
        try {
            return MapperHolder.mapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
