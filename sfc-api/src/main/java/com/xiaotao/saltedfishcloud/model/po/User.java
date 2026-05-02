package com.xiaotao.saltedfishcloud.model.po;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.xiaotao.saltedfishcloud.constant.UserConstants;
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

    /** @deprecated Use {@link UserConstants#SYS_NAME_PUBLIC} */
    @Deprecated
    public static final String SYS_NAME_PUBLIC = UserConstants.SYS_NAME_PUBLIC;
    /** @deprecated Use {@link UserConstants#SYS_NAME_ADMIN} */
    @Deprecated
    public static final String SYS_NAME_ADMIN = UserConstants.SYS_NAME_ADMIN;
    /** @deprecated Use {@link UserConstants#SYS_GROUP_NAME_PUBLIC} */
    @Deprecated
    public static final String SYS_GROUP_NAME_PUBLIC = UserConstants.SYS_GROUP_NAME_PUBLIC;
    /** @deprecated Use {@link UserConstants#PUBLIC_USER_ID} */
    @Deprecated
    public static final long PUBLIC_USER_ID = UserConstants.PUBLIC_USER_ID;
    /** @deprecated Use {@link UserConstants#TYPE_ADMIN} */
    @Deprecated
    public static final int TYPE_ADMIN = UserConstants.TYPE_ADMIN;
    /** @deprecated Use {@link UserConstants#TYPE_COMMON} */
    @Deprecated
    public static final int TYPE_COMMON = UserConstants.TYPE_COMMON;

    @Serial
    private static final long serialVersionUID = -2530285292010387981L;

    private static final User PUBLIC_USER_INST;

    static {
        PUBLIC_USER_INST = new User() {{
            super.setId(UserConstants.PUBLIC_USER_ID);
            super.setUser(UserConstants.SYS_NAME_PUBLIC);
        }};
    }

    public static User getPublicUser() {
        return PUBLIC_USER_INST;
    }

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    public boolean isAdmin() {
        return UserConstants.TYPE_ADMIN == (this.getType() == null ? UserConstants.TYPE_COMMON : this.getType());
    }

    @JsonIgnore
    public boolean isPublicUser() {
        return this.getId() != null && this.getId() == UserConstants.PUBLIC_USER_ID;
    }
}
