package com.xiaotao.saltedfishcloud.model.po;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sfc.cloud.constant.SfcUserConstants;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

import jakarta.validation.Valid;

import java.io.Serial;

@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@AllArgsConstructor
@Valid
public class User extends UserInfo {


    @Serial
    private static final long serialVersionUID = -2530285292010387981L;

    private static final User PUBLIC_USER_INST;

    static {
        PUBLIC_USER_INST = new User() {{
            super.setId(SfcUserConstants.PUBLIC_USER_ID);
            super.setUser(SfcUserConstants.SYS_NAME_PUBLIC);
        }};
    }

    public static User getPublicUser() {
        return PUBLIC_USER_INST;
    }

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    public boolean isAdmin() {
        return SfcUserConstants.TYPE_ADMIN == (this.getType() == null ? SfcUserConstants.TYPE_COMMON : this.getType());
    }

    @JsonIgnore
    public boolean isPublicUser() {
        return this.getId() != null && this.getId() == SfcUserConstants.PUBLIC_USER_ID;
    }
}
