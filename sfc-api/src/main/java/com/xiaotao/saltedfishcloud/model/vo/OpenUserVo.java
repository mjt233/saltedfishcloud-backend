package com.xiaotao.saltedfishcloud.model.vo;

import com.xiaotao.saltedfishcloud.model.po.UserPrincipal;
import lombok.Data;

@Data
public class OpenUserVo {
    private Long id;

    private String username;

    private String email;

    private String avatar;

    public static OpenUserVo of(UserPrincipal user) {
        OpenUserVo vo = new OpenUserVo();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setEmail(user.getEmail());
        return vo;
    }
}
