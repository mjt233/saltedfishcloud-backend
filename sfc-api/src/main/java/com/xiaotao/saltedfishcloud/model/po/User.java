package com.xiaotao.saltedfishcloud.model.po;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.xiaotao.saltedfishcloud.utils.JwtUtils;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import com.xiaotao.saltedfishcloud.validator.annotations.Username;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import javax.validation.Valid;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;


@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Valid
public class User implements UserDetails {
    public static final String SYS_NAME_PUBLIC = "__SYSTEM_PUBLIC";
    public static final String SYS_NAME_ADMIN = "ADMIN";
    public static final String SYS_GROUP_NAME_PUBLIC = "__SYSTEM_PUBLIC_GROUP";
    public static final long PUBLIC_USER_ID = 0;

    private static final long serialVersionUID = -2530285292010387981L;
    public static final int TYPE_ADMIN = 1;
    public static final int TYPE_COMMON = 0;

    /**
     * 用户id，后续将改为Long类型
     */
    private Long id;
    @Username
    private String user;

    @JsonIgnore
    private String pwd;
    private Integer lastLogin;
    private Integer type = User.TYPE_COMMON;
    private int quota;
    private String email;

    @JsonIgnore
    private String token;

    private static final User PUBLIC_USER_INST;

    static {
        PUBLIC_USER_INST = new User(){
            {
                super.setId(PUBLIC_USER_ID);
                super.setUser(SYS_NAME_PUBLIC);
            }
            @Override
            public void setEmail(String email) {
                throw new UnsupportedOperationException("不支持设置公共用户属性");
            }

            @Override
            public void setQuota(int quota) {
                throw new UnsupportedOperationException("不支持设置公共用户属性");
            }

            @Override
            public void setLastLogin(Integer lastLogin) {
                throw new UnsupportedOperationException("不支持设置公共用户属性");
            }

            @Override
            public void setPwd(String pwd) {
                throw new UnsupportedOperationException("不支持设置公共用户属性");
            }

            @Override
            public void setUser(String user) {
                throw new UnsupportedOperationException("不支持设置公共用户属性");
            }

            @Override
            public void setId(Long id) {
                throw new UnsupportedOperationException("不支持设置公共用户属性");
            }

            @Override
            public void setType(Integer type) {
                throw new UnsupportedOperationException("不支持设置公共用户属性");
            }
        };
    }

    @lombok.Setter(AccessLevel.NONE)
    protected List<SimpleGrantedAuthority> authorities = new LinkedList<>();

    public void setType(Integer type) {
        authorities.add(new SimpleGrantedAuthority("ROLE_COMMON"));
        if (type == TYPE_ADMIN) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        this.type = type;
    }

    /**
     * 是否具备管理员权限
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    public boolean isAdmin() {
        return TYPE_ADMIN == this.type;
    }

    /**
     * 获取公共用户信息实例
     */
    public static User getPublicUser() {
        return PUBLIC_USER_INST;
    }

    @JsonIgnore
    public String toToken() {
        try {
            String json = MapperHolder.mapper.writeValueAsString(this);
            return JwtUtils.generateToken(json);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    @JsonIgnore
    public boolean isPublicUser() {
        return id == 0;
    }

    @Override
    @JsonIgnore
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    @JsonIgnore
    public String getPassword() {
        return getPwd();
    }

    @Override
    @JsonIgnore
    public String getUsername() {
        return getUser();
    }

    @Override
    @JsonIgnore
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    @JsonIgnore
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    @JsonIgnore
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    @JsonIgnore
    public boolean isEnabled() {
        return true;
    }
}
