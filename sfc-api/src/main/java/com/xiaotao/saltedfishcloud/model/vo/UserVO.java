package com.xiaotao.saltedfishcloud.model.vo;

import com.sfc.cloud.constant.SfcUserConstants;
import com.xiaotao.saltedfishcloud.model.po.User;
import lombok.Data;
import org.springframework.beans.BeanUtils;

@Data
public class UserVO {
    private Long id;

    private String user;

    private String email;

    private String token;

    /**
     * @see com.sfc.cloud.constant.SfcUserConstants#TYPE_ADMIN
     * @see com.sfc.cloud.constant.SfcUserConstants#TYPE_COMMON
     */
    private Integer type;

    /**
     * 是否为管理员权限账号
     */
    public boolean isAdmin() {
        return type == SfcUserConstants.TYPE_ADMIN;
    }

    public static UserVO from(User user, boolean ignoreToken) {
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        if (ignoreToken) {
            userVO.setToken(null);
        }
        return userVO;
    }

    public static UserVO from(User user) {
        return from(user, false);
    }
}
