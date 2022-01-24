package com.xiaotao.saltedfishcloud.service.file.impl.store;

import com.xiaotao.saltedfishcloud.dao.mybatis.UserDao;
import com.xiaotao.saltedfishcloud.entity.po.User;
import com.xiaotao.saltedfishcloud.entity.po.file.BasicFileInfo;
import com.xiaotao.saltedfishcloud.entity.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.exception.UnableOverwriteException;
import com.xiaotao.saltedfishcloud.exception.UserNoExistException;
import com.xiaotao.saltedfishcloud.service.file.StoreService;
import com.xiaotao.saltedfishcloud.service.file.impl.store.path.PathHandler;
import com.xiaotao.saltedfishcloud.utils.DiskFileUtils;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RAWStoreService implements StoreService {
    private final UserDao userDao;

    @Override
    public List<FileInfo> lists(int uid, String path) throws IOException {
        String p = LocalStoreConfig.rawPathHandler.getStorePath(uid, path, null);
        Path p2 = Paths.get(p);
        if (!Files.exists(p2) || !Files.isDirectory(p2)) {
            return null;
        }
        List<FileInfo> res = new ArrayList<>();
        User user = userDao.getUserById(uid);
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
