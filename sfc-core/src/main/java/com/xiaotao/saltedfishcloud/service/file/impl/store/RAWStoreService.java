package com.xiaotao.saltedfishcloud.service.file.impl.store;

import com.xiaotao.saltedfishcloud.constant.error.AccountError;
import com.xiaotao.saltedfishcloud.entity.po.User;
import com.xiaotao.saltedfishcloud.entity.po.file.BasicFileInfo;
import com.xiaotao.saltedfishcloud.entity.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.exception.UnableOverwriteException;
import com.xiaotao.saltedfishcloud.exception.UserNoExistException;
import com.xiaotao.saltedfishcloud.service.file.StoreService;
import com.xiaotao.saltedfishcloud.service.file.impl.store.path.PathHandler;
import com.xiaotao.saltedfishcloud.service.user.UserService;
import com.xiaotao.saltedfishcloud.utils.DiskFileUtils;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import com.xiaotao.saltedfishcloud.validator.FileValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.xiaotao.saltedfishcloud.service.file.impl.store.LocalStoreConfig.ACCEPT_AVATAR_TYPE;

@Slf4j
public class RAWStoreService implements StoreService {
    @Autowired
    private UserService userService;

    private final static Resource DEFAULT_AVATAR_RESOURCE = new ClassPathResource("/static/static/defaultAvatar.png");


    @Override
    public Resource getAvatar(int uid) {
        User user = userService.getUserById(uid);
        if (user == null) {
            throw new JsonException(AccountError.USER_NOT_EXIST);
        }
        String profilePath = LocalStoreConfig.getUserProfileRoot(user.getUsername());
        File[] avatars = new File(profilePath).listFiles(pathname -> pathname.getName().contains("avatar"));
        if (avatars == null || avatars.length == 0) {
            return DEFAULT_AVATAR_RESOURCE;
        } else {
            return new PathResource(avatars[0].getPath());
        }
    }

    @Override
    public void saveAvatar(int uid, Resource resource) throws IOException {
        FileValidator.validateAvatar(resource);
        String suffix = FileUtils.getSuffix(resource.getFilename());
        if ( !ACCEPT_AVATAR_TYPE.contains(suffix)) {
            throw new JsonException(400, "不支持的格式，只支持jpg, jpeg, gif, png");
        }
        String username = userService.getUserById(uid).getUsername();

        try {
            Path profileRoot = Paths.get(LocalStoreConfig.getUserProfileRoot(username));
            Files.createDirectories(profileRoot);
            File[] avatars = profileRoot.toFile().listFiles(pathname -> pathname.getName().contains("avatar"));
            if (avatars != null && avatars.length != 0) {
                avatars[0].delete();
            }
            Files.copy(resource.getInputStream(), Paths.get(profileRoot + "/avatar." + suffix), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new JsonException(500, e.getMessage());
        }
    }

    @Override
    public List<FileInfo> lists(int uid, String path) throws IOException {
        String p = LocalStoreConfig.rawPathHandler.getStorePath(uid, path, null);
        Path p2 = Paths.get(p);
        if (!Files.exists(p2) || !Files.isDirectory(p2)) {
            return null;
        }
        List<FileInfo> res = new ArrayList<>();
        User user = userService.getUserById(uid);
        if (user == null) throw new UserNoExistException(uid + "");
        Files.list(p2).forEach(e -> {
            FileInfo fi = FileInfo.getLocal(e.toString(), false);
            fi.setPath(DiskFileUtils.getRelativePath(user, e.toString()));
            res.add(fi);
        });

        return res;
    }

    @Override
    public Resource getResource(int uid, String path, String name) {
        String storePath = LocalStoreConfig.rawPathHandler.getStorePath(uid, path + "/" + name, null);
        Path p = Paths.get(storePath);
        if (!Files.exists(p) || Files.isDirectory(p)) {
            return null;
        }
        return new PathResource(p);
    }

    @Override
    public boolean exist(int uid, String path) {
        String p = LocalStoreConfig.rawPathHandler.getStorePath(uid, path, null);
        return Files.exists(Paths.get(p));
    }

    @Override
    public void moveToSave(int uid, Path nativePath, String diskPath, BasicFileInfo fileInfo) throws IOException {
        Path targetPath = Paths.get(LocalStoreConfig.rawPathHandler.getStorePath(uid, diskPath, fileInfo)); // 被移动到的目标位置
        if (Files.exists(targetPath) && Files.isDirectory(targetPath)) {
            throw new IllegalArgumentException("被覆盖的目标 " + fileInfo.getName() + " 是个目录");
        }
        // 非唯一模式，直接将文件移动到目标位置
        if (!nativePath.equals(targetPath)) {
            log.debug("File move {} => {}", nativePath, targetPath);
            Files.move(nativePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public void copy(int uid, String source, String target, int targetId, String sourceName, String targetName, Boolean overwrite) throws IOException {
        String localSource = LocalStoreConfig.getPathHandler().getStorePath(uid, source, null);
        String localTarget = LocalStoreConfig.getPathHandler().getStorePath(targetId, target, null);
        FileUtils.copy(Paths.get(localSource),Paths.get(localTarget), sourceName, targetName, false);
    }

    @Override
    public void store(int uid, InputStream input, String targetDir, FileInfo fileInfo) throws IOException {
        Path rawTarget = Paths.get(LocalStoreConfig.rawPathHandler.getStorePath(uid, targetDir, fileInfo));
        if (Files.exists(rawTarget) && Files.isDirectory(rawTarget)) {
            throw new UnableOverwriteException(409, "已存在同名目录: " + targetDir + "/" + fileInfo.getName());
        }
        FileUtils.createParentDirectory(rawTarget);
        log.debug("save file:" + rawTarget);
        Files.copy(input, rawTarget, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public void move(int uid, String source, String target, String name, boolean overwrite) throws IOException {
        PathHandler pathHandler = LocalStoreConfig.rawPathHandler;
        BasicFileInfo fileInfo = new BasicFileInfo(name, null);
        Path sourcePath = Paths.get(pathHandler.getStorePath(uid, source, fileInfo));
        Path targetPath = Paths.get(pathHandler.getStorePath(uid, target, fileInfo));
        FileUtils.move(sourcePath, targetPath);
    }

    @Override
    public void rename(int uid, String path, String oldName, String newName) throws IOException {
        String base = LocalStoreConfig.rawPathHandler.getStorePath(uid, path, null);
        FileUtils.rename(base, oldName, newName);
    }

    @Override
    public boolean mkdir(int uid, String path, String name) throws IOException {
        Path localFilePath = Paths.get(LocalStoreConfig.rawPathHandler.getStorePath(uid, path, null) + "/" + name);
        if (Files.exists(localFilePath)) {
            return Files.isDirectory(localFilePath);
        }
        Files.createDirectories(localFilePath);
        return true;
    }

    @Override
    public int delete(String md5) throws IOException {
        return DiskFileUtils.delete(md5);
    }

    @Override
    public long delete(int uid, String path, Collection<String> files) throws IOException {
        String base = LocalStoreConfig.rawPathHandler.getStorePath(uid, path, null);
        int cnt = 0;
        for (String file : files) {
            Path fullPath = Paths.get(base + "/" + file);
            cnt += FileUtils.delete(fullPath);
        }
        return cnt;
    }
}
