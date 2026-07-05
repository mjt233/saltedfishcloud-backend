package com.xiaotao.saltedfishcloud.model.po;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.xiaotao.saltedfishcloud.constant.UserConstants;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

/**
 * Spring Security 认证主体 + JWT 载荷 DTO。
 * 从 {@link User} 构建，不依赖 JPA，专用于安全上下文和 token 序列化。
 */
@JsonTypeName("UserPrincipal")
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
@Data
@NoArgsConstructor
public class UserPrincipal implements UserDetails {

    /** 用户唯一标识 */
    private Long id;

    /** 用户登录名，JSON 序列化时映射为 {@code "user"} */
    @JsonProperty("user")
    private String username;

    /** 用户密码，仅内部使用，不参与 JSON 序列化 */
    @JsonIgnore
    private String password;

    /** 用户类型，参见 {@link UserConstants}，决定角色分配 */
    private Integer type = UserConstants.TYPE_COMMON;

    /** 用户邮箱 */
    private String email;

    /** 用户配额（字节） */
    private Long quota;

    /** 最后登录时间戳（秒） */
    private Integer lastLogin;

    /** 账号创建时间 */
    private Date createAt;

    /** 临时令牌（如 JWT），不参与 JSON 序列化 */
    @JsonIgnore
    private String token;

    /** 用户角色列表，由 {@link #setType(Integer)} 根据 type 自动维护 */
    private List<SimpleGrantedAuthority> authorities = new LinkedList<>();

    public void setType(Integer type) {
        this.type = type;
        authorities.clear();
        authorities.add(new SimpleGrantedAuthority("ROLE_COMMON"));
        if (type != null && type == UserConstants.TYPE_ADMIN) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
    }

    public boolean isAdmin() {
        return UserConstants.TYPE_ADMIN == (this.type == null ? UserConstants.TYPE_COMMON : this.type);
    }

    @JsonIgnore
    public boolean isPublicUser() {
        return this.id != null && this.id == UserConstants.PUBLIC_USER_ID;
    }

    @Override
    @JsonIgnore
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    @JsonIgnore
    public String getPassword() {
        return password;
    }

    @Override
    @JsonIgnore
    public String getUsername() {
        return username;
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

    /** 从 JPA 实体构建 */
    public static UserPrincipal from(User user) {
        if (user == null) {
            return null;
        }
        UserPrincipal principal = new UserPrincipal();
        principal.setId(user.getId());
        principal.setUsername(user.getUser());
        principal.setPassword(user.getPwd());
        principal.setType(user.getType());
        principal.setEmail(user.getEmail());
        principal.setQuota(user.getQuota());
        principal.setLastLogin(user.getLastLogin());
        principal.setCreateAt(user.getCreateAt());
        return principal;
    }

    /** 构建公共用户实例 */
    public static UserPrincipal publicUser() {
        UserPrincipal principal = new UserPrincipal();
        principal.setId(UserConstants.PUBLIC_USER_ID);
        principal.setUsername(UserConstants.SYS_NAME_PUBLIC);
        return principal;
    }
}
