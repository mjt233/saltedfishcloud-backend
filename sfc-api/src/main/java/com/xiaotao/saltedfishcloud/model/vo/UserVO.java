package com.xiaotao.saltedfishcloud.model.vo;

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
     * @see com.xiaotao.saltedfishcloud.model.po.User#TYPE_ADMIN
     * @see com.xiaotao.saltedfishcloud.model.po.User#TYPE_COMMON
     */
    private Integer type;

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
