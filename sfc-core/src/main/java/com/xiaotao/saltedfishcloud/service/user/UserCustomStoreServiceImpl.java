package com.xiaotao.saltedfishcloud.service.user;

import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.service.file.UserCustomStoreService;
import com.xiaotao.saltedfishcloud.service.file.store.attach.AttachStorage;
import com.xiaotao.saltedfishcloud.service.file.store.attach.AttachStorageDomainDefinition;
import com.xiaotao.saltedfishcloud.service.file.store.attach.AttachStorageManager;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;

/**
 * 默认的用户自定义附属数据存储实现，将头像文件存放到 user_avatar 存储域下，
 */
@Service
@Slf4j
public class UserCustomStoreServiceImpl implements UserCustomStoreService {
    private final static String LOG_PREFIX = "[UserCustomStore]";
    private final static Set<String> ACCEPT_TYPE = Set.of("jpg", "jpeg", "png", "gif");
    private final AttachStorage avatarStorage;
    private final static Resource DEFAULT_AVATAR = new ClassPathResource("/static/defaultAvatar.png");

    public UserCustomStoreServiceImpl(AttachStorageManager attachStorageManager) {
        String userAvatarDomainId = "user_avatar";
        attachStorageManager.registerStorageDomain(AttachStorageDomainDefinition.builder()
                .id(userAvatarDomainId)
                .name("用户头像")
                .build());
        avatarStorage = attachStorageManager.getStorage(userAvatarDomainId);
    }

    /**
     * 获取用户的统一资源前缀目录路径
     */
    private String getUserStoragePathPrefix(long uid) {
        return String.valueOf(uid % 512);
    }

    private String getUserAvatarPath(long uid, String fileType) {
        // 头像位置： 用户前缀/uid.头像图片文件类型
        return this.getUserStoragePathPrefix(uid) + "/" + uid + "." + fileType;
    }

    @Override
    public Resource getAvatar(long uid) throws IOException {
        return ACCEPT_TYPE
                .stream()
                .map(type -> getUserAvatarPath(uid, type))
                .map(path -> {
                    try {
                        return avatarStorage.getFile(path).orElse(null);
                    } catch (IOException e) {
                        log.error("{} 获取用户头像失败 {}", LOG_PREFIX, path, e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .findAny()
                .orElse(null);
    }


    @Override
    public void saveAvatar(long uid, Resource resource) throws IOException {
        String fileType = FileUtils.getSuffix(Objects.requireNonNull(resource.getFilename())).toLowerCase();
        if (!ACCEPT_TYPE.contains(fileType)) {
            throw new JsonException("只接受类型: " + String.join(" ", ACCEPT_TYPE));
        }
        avatarStorage.saveFile(getUserAvatarPath(uid, fileType), resource);
    }

    @Override
    public Resource getDefaultAvatar() throws IOException {
        return DEFAULT_AVATAR;
    }
}
