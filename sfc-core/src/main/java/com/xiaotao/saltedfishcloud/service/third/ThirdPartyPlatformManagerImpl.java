package com.xiaotao.saltedfishcloud.service.third;

import com.xiaotao.saltedfishcloud.cache.CacheKeyPrefixes;
import com.xiaotao.saltedfishcloud.cache.CacheService;
import com.xiaotao.saltedfishcloud.constant.UserConstants;
import com.xiaotao.saltedfishcloud.dao.jpa.ThirdPartyAuthPlatformRepo;
import com.xiaotao.saltedfishcloud.dao.jpa.ThirdPartyPlatformUserRepo;
import com.xiaotao.saltedfishcloud.dao.redis.TokenService;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.model.po.*;
import com.xiaotao.saltedfishcloud.model.vo.UserVO;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.service.file.store.attach.AttachStorage;
import com.xiaotao.saltedfishcloud.service.file.store.attach.AttachStorageDomainDefinition;
import com.xiaotao.saltedfishcloud.service.file.store.attach.AttachStorageManager;
import com.xiaotao.saltedfishcloud.service.log.LogLevel;
import com.xiaotao.saltedfishcloud.service.log.LogRecordManager;
import com.xiaotao.saltedfishcloud.service.third.model.ThirdPartyPlatformCallbackResult;
import com.xiaotao.saltedfishcloud.service.user.UserService;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.xiaotao.saltedfishcloud.utils.StreamCopyResult;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import com.xiaotao.saltedfishcloud.utils.identifier.IdUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamSource;
import org.springframework.data.util.Lazy;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
@Slf4j
public class ThirdPartyPlatformManagerImpl implements ThirdPartyPlatformManager {
    private final static String LOG_TYPE = "第三方登录";
    private final static String LOG_PREFIX = "[第三方登录]";

    private final static String ACTION_RECORD_KEY = CacheKeyPrefixes.THIRD_ACTION;

    @Autowired
    private ThirdPartyAuthPlatformRepo platformRepo;

    @Autowired
    private ThirdPartyPlatformUserRepo platformUserRepo;

    @Autowired
    private UserService userService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private LogRecordManager logRecordManager;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private DiskFileSystemManager diskFileSystemManager;

    @Autowired
    private CacheService cacheService;

    /**
     * 第三方平台头像缓存附属存储。
     */
    private AttachStorage thirdPlatformAvatarStorage;

    /**
     * 注册第三方平台头像缓存附属存储域。
     *
     * @param attachStorageManager 附属存储管理器
     */
    @Autowired
    public void setAttachStorageManager(AttachStorageManager attachStorageManager) {
        attachStorageManager.registerStorageDomain(AttachStorageDomainDefinition.builder()
                .id("third_platform_avatar")
                .name("第三方平台头像缓存")
                .description("第三方登录头像缓存")
                .build());
        thirdPlatformAvatarStorage = attachStorageManager.getStorage("third_platform_avatar");
    }

    private final Map<String, ThirdPartyPlatformHandler> handlerMap = new ConcurrentHashMap<>();

    @Autowired(required = false)
    public void setThirdPartyPlatformHandler(List<ThirdPartyPlatformHandler> handlers) {
        if (handlers == null || handlers.isEmpty()) {
            return;
        }
        handlers.forEach(this::registerPlatformHandler);
    }

    @Override
    public void registerPlatformHandler(ThirdPartyPlatformHandler handler) {
        if(handlerMap.containsKey(handler.getType())) {
            throw new IllegalArgumentException("类型为" + handler.getType() + "的第三方平台已注册");
        }
        handlerMap.put(handler.getType(), handler);
        log.info("{}注册第三方平台账号登录: {}", LOG_PREFIX, handler.getType());
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
        } catch (Exception e) {
            log.error("{}平台{}回调出错", LOG_PREFIX, platformType, e);
            throw new RuntimeException("系统内部错误，请联系管理员检查系统日志");
        }

        if (platformUser == null) {
            throw new IllegalArgumentException("认证失败，无法获取平台用户信息");
        }

        // 缓存第三方平台头像
        cacheAvatar(platformUser);

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
                String token = tokenService.generateUserToken(UserPrincipal.from(assocUser));
                UserVO userVO = UserVO.from(assocUser);
                userVO.setToken(token);
                result = ThirdPartyPlatformCallbackResult.builder()
                        .isNewUser(false)
                        .newToken(token)
                        .platformUser(platformUser)
                        .user(userVO)
                        .actionId(actionId)
                        .build();
                logRecordManager.saveRecordAsync(LogRecord.builder()
                                .type(LOG_TYPE)
                                .level(LogLevel.INFO)
                                .msgAbstract("[" + assocUser.getUser() + "]通过[" + platformType + "]登录")
                                .msgDetail(MapperHolder.toJsonNoEx(result))
                        .build());
            } else {
                // 登录过但未关联系统账号
                result = ThirdPartyPlatformCallbackResult.builder()
                        .isNewUser(true)
                        .platformUser(existPlatformUser)
                        .actionId(actionId)
                        .build();
                logRecordManager.saveRecordAsync(LogRecord.builder()
                        .type(LOG_TYPE)
                        .level(LogLevel.INFO)
                        .msgAbstract("未绑定咸鱼云的用户通过[" + platformType + "]的[" + platformUser.getUserName() + "]登录")
                        .msgDetail(MapperHolder.toJsonNoEx(result))
                        .build());
            }
        } else {
            platformUser.setIsActive(false);
            // 判断当前是否已有用户登录，如果有则记录当前登录用户（仅设置对象信息，确认关联操作需要前端另外调用 bindUser以供用户确认）
            UserPrincipal curUser = SecureUtils.getSpringSecurityUser();
            if (curUser != null) {
                assocUser = userService.getUserById(curUser.getId());
                platformUser.setUid(assocUser.getId());
            } else if (StringUtils.hasText(platformUser.getEmail())) {
                // 如果系统存在相同邮箱，默认匹配让用户确认
                assocUser = userService.getUserByEmail(platformUser.getEmail());
                if (assocUser != null) {
                    platformUser.setUid(assocUser.getId());
                }
            }
            if (assocUser == null && !Boolean.TRUE.equals(platform.getIsAllowRegister())) {
                throw new JsonException(platform.getName() + " 未开放新用户注册，请联系管理员");
            }

            if (platformUser.getStatus() == null) {
                platformUser.setStatus(1);
            }

            result = ThirdPartyPlatformCallbackResult.builder()
                    .isNewUser(true)
                    .platformUser(platformUser)
                    .user(UserVO.from(assocUser))
                    .actionId(actionId)
                    .build();
            logRecordManager.saveRecordAsync(LogRecord.builder()
                    .type(LOG_TYPE)
                    .level(LogLevel.INFO)
                    .msgAbstract("新用户通过[" + platformType + "]的[" + platformUser.getUserName() + "]登录")
                    .msgDetail(MapperHolder.toJsonNoEx(result))
                    .build());
        }

        setCallbackResult(actionId, result);
        return result;
    }

    /**
     * 根据第三方平台用户信息生成咸鱼云的新用户名，会优先尝试使用第三方平台的名称，系统中存在重名时则随机生成
     */
    private String generateNewUserName(ThirdPartyPlatformUser platformUser) {
        User sameNameUser = userService.getUserByUser(platformUser.getUserName());
        if (sameNameUser == null) {
            return platformUser.getUserName();
        } else {
            return platformUser.getPlatformType() + "_" + Optional.ofNullable(platformUser.getThirdPartyUserId()).orElseGet(() -> String.valueOf(IdUtil.getId()));
        }
    }

    /**
     * 缓存用户头像，将头像数据缓存到网盘的存储系统并转为base64
     */
    private void cacheAvatar(ThirdPartyPlatformUser user) {
        if(!StringUtils.hasText(user.getAvatarUrl()) || user.getAvatarUrl().startsWith("data:")) {
            return;
        }

        try {
            String cacheFilePath = SecureUtils.getMd5(user.getAvatarUrl());
            InputStreamSource inputStreamSource;
            if (!thirdPlatformAvatarStorage.exist(cacheFilePath)) {
                // 未缓存头像，从原始URL读取数据后存入
                restTemplate.execute(user.getAvatarUrl(), HttpMethod.GET, null, response -> {
                    thirdPlatformAvatarStorage.saveFile(cacheFilePath, out -> {
                        try (InputStream is = response.getBody()) {
                            return new StreamCopyResult(StreamUtils.copy(is, out), null);
                        }
                    });
                    return null;
                });
            }
            inputStreamSource = thirdPlatformAvatarStorage.getFile(cacheFilePath).orElse(null);
            if (inputStreamSource == null) {
                return;
            }
            try (InputStream is = inputStreamSource.getInputStream()) {
                user.setAvatarUrl("data:image/jpeg;base64," + Base64.getEncoder().encodeToString(StreamUtils.copyToByteArray(is)));
            }
        } catch (Exception e) {
            log.error("{} 缓存第三方平台头像失败", LOG_PREFIX, e);
        }
    }

    @Override
    public List<ThirdPartyAuthPlatform> listPlatform() {
        Lazy<Map<String, ThirdPartyAuthPlatform>> configMap = Lazy.of(() -> platformRepo.findAll()
                .stream()
                .collect(Collectors.toMap(ThirdPartyAuthPlatform::getType, e -> e))
        );
        return getAllPlatformHandler().stream()
                .map(e -> configMap.get().getOrDefault(e.getType(), e.getDefaultConfig()))
                .filter(Objects::nonNull)
                .map(c -> {
                    ThirdPartyAuthPlatform platform = new ThirdPartyAuthPlatform();
                    BeanUtils.copyProperties(c, platform);
                    platform.setAuthUrl(getHandler(c.getType()).getAuthUrl(c));
                    return platform;
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO bindUser(String actionId, @Nullable User user) {
        ThirdPartyPlatformCallbackResult callbackResult = Objects.requireNonNull(getCallbackResult(actionId), "无效或已过期的actionId");
        ThirdPartyPlatformUser platformUser = callbackResult.getPlatformUser();
        UserVO bindUserObj = Optional.ofNullable(callbackResult.getUser()).orElse(UserVO.from(user));
        if (bindUserObj == null) {
            throw new IllegalArgumentException("未指定关联的用户");
        }

        if(platformUserRepo.getByPlatformTypeAndUid(platformUser.getPlatformType(), bindUserObj.getId()) != null) {
            throw new IllegalArgumentException("用户" + bindUserObj.getUser() + "已关联了" + platformUser.getPlatformType() + "平台的账号，无需重复关联");
        }

        platformUser.setIsActive(true);
        platformUser.setUid(bindUserObj.getId());
        platformUserRepo.save(platformUser);
        bindUserObj.setToken(tokenService.generateUserToken(bindUserObj));
        logRecordManager.saveRecordAsync(LogRecord.builder()
                .type(LOG_TYPE)
                .level(LogLevel.INFO)
                .msgAbstract("用户[" + bindUserObj.getUser() + "]绑定[" + callbackResult.getPlatformUser().getPlatformType() + "]的[" + platformUser.getUserName() + "]")
                .msgDetail(MapperHolder.toJsonNoEx(new HashMap<>(){{
                    put("callbackResult", callbackResult);
                    put("bindUserObj", bindUserObj);
                }}))
                .build());
        return bindUserObj;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO createUser(String actionId) {
        ThirdPartyPlatformCallbackResult callbackResult = getCallbackResult(actionId);
        ThirdPartyPlatformUser platformUser = callbackResult.getPlatformUser();
        String newUserName = this.generateNewUserName(platformUser);
        userService.addUser(newUserName, IdUtil.getId() + "", platformUser.getEmail(), UserConstants.TYPE_COMMON);
        User newUser = userService.getUserByUser(newUserName);
        UserVO createResultVO = UserVO.from(newUser, false);
        createResultVO.setToken(tokenService.generateUserToken(UserPrincipal.from(newUser)));

        platformUser.setUid(newUser.getId());
        platformUser.setIsActive(true);
        platformUserRepo.save(platformUser);

        clearActionId(actionId);
        return createResultVO;
    }

    private ThirdPartyPlatformCallbackResult getCallbackResult(String actionId) {
        return Objects.requireNonNull(
                cacheService.get(ACTION_RECORD_KEY + actionId),
                "操作已过期或记录id不存在，请重试"
        );
    }

    private void setCallbackResult(String actionId, ThirdPartyPlatformCallbackResult result) {
        cacheService.set(ACTION_RECORD_KEY + actionId, result, 10, TimeUnit.MINUTES);
    }

    private void clearActionId(String actionId) {
        cacheService.delete(ACTION_RECORD_KEY + actionId);
    }
}
