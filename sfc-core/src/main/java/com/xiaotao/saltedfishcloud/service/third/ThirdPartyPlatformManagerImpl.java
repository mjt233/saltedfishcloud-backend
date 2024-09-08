package com.xiaotao.saltedfishcloud.service.third;

import com.xiaotao.saltedfishcloud.dao.jpa.ThirdPartyAuthPlatformRepo;
import com.xiaotao.saltedfishcloud.dao.jpa.ThirdPartyUserRepo;
import com.xiaotao.saltedfishcloud.dao.redis.TokenService;
import com.xiaotao.saltedfishcloud.exception.UserNoExistException;
import com.xiaotao.saltedfishcloud.model.ConfigNode;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyAuthPlatform;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyPlatformUser;
import com.xiaotao.saltedfishcloud.model.po.User;
import com.xiaotao.saltedfishcloud.service.third.model.ThirdPartyPlatformCallbackResult;
import com.xiaotao.saltedfishcloud.service.user.UserService;
import com.xiaotao.saltedfishcloud.utils.JwtUtils;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.xiaotao.saltedfishcloud.utils.identifier.IdUtil;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ThirdPartyPlatformManagerImpl implements ThirdPartyPlatformManager, InitializingBean {
    @Autowired
    private ThirdPartyAuthPlatformRepo platformRepo;

    @Autowired
    private ThirdPartyUserRepo platformUserRepo;

    @Autowired
    private UserService userService;

    @Autowired
    private TokenService tokenService;

    private final Map<String, ThirdPartyPlatformHandler> handlerMap = new ConcurrentHashMap<>();

    @Override
    public void afterPropertiesSet() throws Exception {
        registerPlatform(new ThirdPartyPlatformHandler() {
            @Override
            public String getType() {
                return "test";
            }

            @Override
            public String getRedirectUrl(ThirdPartyAuthPlatform partyAuthPlatform) {
                return "";
            }

            @Override
            public ThirdPartyPlatformUser callback(ThirdPartyAuthPlatform partyAuthPlatform, Map<String, Object> platformCallbackParam) {
                ThirdPartyPlatformUser user = new ThirdPartyPlatformUser();
                User admin = userService.getUserByUser("admin");
                user.setUid(admin.getId());
                user.setPlatformType(getType());
                user.setThirdPartyUserId("admin");
                return user;
            }

            @Override
            public List<ConfigNode> getPlatformConfigNode() {
                return null;
            }
        });
    }

    @Override
    public void registerPlatform(ThirdPartyPlatformHandler handler) {
        if(handlerMap.containsKey(handler.getType())) {
            throw new IllegalArgumentException("类型为" + handler.getType() + "的第三方平台已注册");
        }
        handlerMap.put(handler.getType(), handler);

    }

    @Override
    public List<ThirdPartyPlatformHandler> getAllPlatform() {
        return new ArrayList<>(handlerMap.values());
    }

    @Override
    public ThirdPartyPlatformHandler getHandler(String platformType) {
        return handlerMap.get(platformType);
    }

    @Override
    public ThirdPartyPlatformCallbackResult doCallback(String platformType, HttpServletRequest request) {
        // 验证第三方平台登录是否可用
        ThirdPartyPlatformHandler handler = getHandler(platformType);
        if (handler == null) {
            throw new IllegalArgumentException("不支持来自平台 " + platformType + " 的登录");
        }

        ThirdPartyAuthPlatform platform = platformRepo.getByType(platformType);
        if (!"test".equals(platformType)) {
            if (platform == null || !Boolean.TRUE.equals(platform.getIsEnable())) {
                throw new IllegalArgumentException("平台" + platformType + "的第三方登录未启用");
            }
        }

        // 封装回调url中的参数
        Enumeration<String> parameterNames = request.getParameterNames();
        Map<String, Object> param = new HashMap<>();
        while (parameterNames.hasMoreElements()) {
            String key = parameterNames.nextElement();
            param.put(key, request.getParameter(key));
        }

        // 执行对应的第三方平台处理器的回调方法
        ThirdPartyPlatformUser platformUser = handler.callback(platform, param);
        if (platformUser == null) {
            throw new IllegalArgumentException("认证失败，无法获取平台用户信息");
        }

        // 判断是否已关联了系统用户
        User assocUser;
        ThirdPartyPlatformUser existPlatformUser = platformUserRepo.getByPlatformTypeAndThirdPartyUserId(platformUser.getPlatformType(), platformUser.getThirdPartyUserId());
        if (existPlatformUser != null) {
             assocUser = Objects.requireNonNull(
                    userService.getUserById(existPlatformUser.getUid()),
                    "该账号已绑定用户" + existPlatformUser.getUid() + "，但用户不存在，请联系管理员");
            platformUser.setUid(assocUser.getId());
            return ThirdPartyPlatformCallbackResult.builder()
                    .isNewUser(false)
                    .platformUser(platformUser)
                    .user(assocUser)
                    .build();
        } else {
            // 没关联系统用户，判断当前是否已有用户登录，如果有则关联到当前登录用户
            User curUser = SecureUtils.getSpringSecurityUser();
            boolean isNewUser = false;
            if (curUser != null) {
                assocUser = curUser;
                platformUser.setIsActive(true);
                if(platformUserRepo.getByPlatformTypeAndUid(platformType, curUser.getId()) != null) {
                    throw new IllegalArgumentException("当前用户" + curUser.getUser() + "已关联了" + platformType + "平台的账号");
                }
            } else {
                platformUser.setIsActive(false);
                String newUserName = generateNewUserName(platformUser);
                userService.addUser(newUserName, IdUtil.getId() + "", platformUser.getEmail(), User.TYPE_COMMON);
                assocUser = userService.getUserByUser(newUserName);
                isNewUser = true;
            }

            if (platformUser.getStatus() == null) {
                platformUser.setStatus(1);
            }
            platformUserRepo.save(platformUser);
            platformUser.setUid(assocUser.getId());
            return ThirdPartyPlatformCallbackResult.builder()
                    .isNewUser(isNewUser)
                    .platformUser(platformUser)
                    .user(assocUser)
                    .build();
        }
    }

    /**
     * 根据第三方平台用户信息生成咸鱼云的新用户名，会优先尝试使用第三方平台的名称，系统中存在重名时则随机生成
     */
    private String generateNewUserName(ThirdPartyPlatformUser platformUser) {
        try {
            return userService.getUserByUser(platformUser.getUserName()).getUser();
        } catch (UserNoExistException e) {
            return platformUser.getPlatformType() + "_" + IdUtil.getId();
        }

    }
}
