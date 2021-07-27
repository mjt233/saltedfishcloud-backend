package com.xiaotao.saltedfishcloud.po;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;


@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User implements UserDetails {
    public static final String SYS_NAME_PUBLIC = "__SYSTEM_PUBLIC";
    public static final String SYS_NAME_ADMIN = "ADMIN";
    public static final String SYS_GROUP_NAME_PUBLIC = "__SYSTEM_PUBLIC_GROUP";

    private static final long serialVersionUID = -2530285292010387981L;
    public static final int TYPE_ADMIN = 1;
    public static final int TYPE_COMMON = 0;
    private Integer id;
    private String user;
    private String pwd;
    private String token;
    private Integer lastLogin;
    private Integer type = User.TYPE_COMMON;
    private int quota;

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
     * 获取公共用户信息实例
     */
    public static User getPublicUser() {
        User user = new User();
        user.setId(0);
        user.setUser(SYS_NAME_PUBLIC);
        return user;
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
