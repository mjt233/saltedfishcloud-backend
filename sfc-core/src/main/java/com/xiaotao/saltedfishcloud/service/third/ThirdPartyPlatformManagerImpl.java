package com.xiaotao.saltedfishcloud.service.third;

import com.xiaotao.saltedfishcloud.dao.jpa.ThirdPartyAuthPlatformRepo;
import com.xiaotao.saltedfishcloud.dao.jpa.ThirdPartyUserRepo;
import com.xiaotao.saltedfishcloud.dao.redis.RedisDao;
import com.xiaotao.saltedfishcloud.dao.redis.TokenService;
import com.xiaotao.saltedfishcloud.exception.UserNoExistException;
import com.xiaotao.saltedfishcloud.model.ConfigNode;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyAuthPlatform;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyPlatformUser;
import com.xiaotao.saltedfishcloud.model.po.User;
import com.xiaotao.saltedfishcloud.service.third.model.ThirdPartyPlatformCallbackResult;
import com.xiaotao.saltedfishcloud.service.user.UserService;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.xiaotao.saltedfishcloud.utils.identifier.IdUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.util.Lazy;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
@Slf4j
public class ThirdPartyPlatformManagerImpl implements ThirdPartyPlatformManager {
    private final static String LOG_PREFIX = "[第三方登录]";

    private final static String ACTION_RECORD_KEY = "third_action::";

    @Autowired
    private ThirdPartyAuthPlatformRepo platformRepo;

    @Autowired
    private ThirdPartyUserRepo platformUserRepo;

    @Autowired
    private UserService userService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private final Map<String, ThirdPartyPlatformHandler> handlerMap = new ConcurrentHashMap<>();

    @Override
    public void registerPlatformHandler(ThirdPartyPlatformHandler handler) {
        if(handlerMap.containsKey(handler.getType())) {
            throw new IllegalArgumentException("类型为" + handler.getType() + "的第三方平台已注册");
        }
        handlerMap.put(handler.getType(), handler);

    }

    @Override
    public List<ThirdPartyPlatformHandler> getAllPlatformHandler() {
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
        if (platform == null || !Boolean.TRUE.equals(platform.getIsEnable())) {
            throw new IllegalArgumentException("平台" + platformType + "的第三方登录未启用");
        }

        // 封装回调url中的参数
        Enumeration<String> parameterNames = request.getParameterNames();
        Map<String, Object> param = new HashMap<>();
        while (parameterNames.hasMoreElements()) {
            String key = parameterNames.nextElement();
            param.put(key, request.getParameter(key));
        }

        // 执行对应的第三方平台处理器的回调方法
        ThirdPartyPlatformUser platformUser;
        try {
            platformUser = handler.callback(platform, param);
        } catch (IOException e) {
            log.error("{}平台{}回调出错", LOG_PREFIX, platformType, e);
            throw new RuntimeException("系统内部错误，请联系管理员检查系统日志");
        }

        if (platformUser == null) {
            throw new IllegalArgumentException("认证失败，无法获取平台用户信息");
        }
        ThirdPartyPlatformCallbackResult result;

        // 判断是否已关联了系统账号
        User assocUser = null;
        String actionId = IdUtil.getId() + "";
        ThirdPartyPlatformUser existPlatformUser = platformUserRepo.getByPlatformTypeAndThirdPartyUserId(platformUser.getPlatformType(), platformUser.getThirdPartyUserId());
        if (existPlatformUser != null) {
            if (Boolean.TRUE.equals(existPlatformUser.getIsActive())) {
                // 该第三方账号已关联系统账号
                if (!Integer.valueOf(1).equals(existPlatformUser.getStatus())) {
                    throw new IllegalArgumentException("该用户在" + platformType + "平台的关联已被停用");
                }
                assocUser = Objects.requireNonNull(
                        userService.getUserById(existPlatformUser.getUid()),
                        "该账号已绑定用户" + existPlatformUser.getUid() + "，但用户不存在，请联系管理员"
                );
                platformUser.setUid(assocUser.getId());
                result = ThirdPartyPlatformCallbackResult.builder()
                        .isNewUser(false)
                        .platformUser(platformUser)
                        .user(assocUser)
                        .actionId(actionId)
                        .build();
            } else {
                // 登录过但未关联系统账号
                result = ThirdPartyPlatformCallbackResult.builder()
                        .isNewUser(true)
                        .platformUser(existPlatformUser)
                        .actionId(actionId)
                        .build();
            }
        } else {
            platformUser.setIsActive(false);
            // 判断当前是否已有用户登录，如果有则记录当前登录用户（仅设置对象信息，确认关联操作需要前端另外调用 bindUser以供用户确认）
            User curUser = SecureUtils.getSpringSecurityUser();
            if (curUser != null) {
                assocUser = curUser;
                platformUser.setUid(assocUser.getId());
            }

            if (platformUser.getStatus() == null) {
                platformUser.setStatus(1);
            }

            result = ThirdPartyPlatformCallbackResult.builder()
                    .isNewUser(true)
                    .platformUser(platformUser)
                    .user(assocUser)
                    .actionId(actionId)
                    .build();
        }

        setPlatformUser(actionId, result.getPlatformUser());
        return result;
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

    @Override
    public List<ThirdPartyAuthPlatform> listPlatform() {
        Lazy<Map<String, ThirdPartyAuthPlatform>> configMap = Lazy.of(() -> platformRepo.findEnabled()
                .stream()
                .collect(Collectors.toMap(ThirdPartyAuthPlatform::getType, e -> e))
        );
        return getAllPlatformHandler().stream()
                .map(e -> configMap.get().getOrDefault(e.getType(), e.getDefaultConfig()))
                .filter(Objects::nonNull)
                .map(c -> {
                    ThirdPartyAuthPlatform platform = new ThirdPartyAuthPlatform();
                    BeanUtils.copyProperties(c, platform);
                    return platform;
                })
                .collect(Collectors.toList());
    }

    @Override
    public void bindUser(String actionId, User user) {
        ThirdPartyPlatformUser platformUser = getPlatformUser(actionId);
        platformUser.setIsActive(true);
        if(platformUserRepo.getByPlatformTypeAndUid(platformUser.getPlatformType(), user.getId()) != null) {
            throw new IllegalArgumentException("用户" + user.getUser() + "已关联了" + platformUser.getPlatformType() + "平台的账号，无需重复关联");
        }
        platformUserRepo.save(platformUser);
        clearActionId(actionId);
    }

    @Override
    public User createUser(String actionId) {
        ThirdPartyPlatformUser platformUser = getPlatformUser(actionId);
        String newUserName = this.generateNewUserName(platformUser);
        userService.addUser(newUserName, IdUtil.getId() + "", platformUser.getEmail(), User.TYPE_COMMON);
        User newUser = userService.getUserByUser(newUserName);
        clearActionId(actionId);
        return newUser;
    }

    private ThirdPartyPlatformUser getPlatformUser(String actionId) {
        return Objects.requireNonNull(
                (ThirdPartyPlatformUser) redisTemplate.opsForValue().get(ACTION_RECORD_KEY + actionId),
                "操作已过期或记录id不存在，请重试"
        );
    }

    private void setPlatformUser(String actionId, ThirdPartyPlatformUser platformUser) {
        redisTemplate.opsForValue().set(ACTION_RECORD_KEY + actionId, platformUser, Duration.ofMinutes(10));
    }

    private void clearActionId(String actionId) {
        redisTemplate.delete(ACTION_RECORD_KEY + actionId);
    }
}
