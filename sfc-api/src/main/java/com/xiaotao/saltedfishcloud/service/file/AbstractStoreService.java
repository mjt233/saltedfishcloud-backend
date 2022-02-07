package com.xiaotao.saltedfishcloud.service.file;

import com.xiaotao.saltedfishcloud.constant.error.FileSystemError;
import com.xiaotao.saltedfishcloud.entity.po.file.BasicFileInfo;
import com.xiaotao.saltedfishcloud.entity.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.Getter;
import lombok.Setter;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

public abstract class AbstractStoreService implements StoreService {
    @Getter
    @Setter
    private int maxDepth = 64;

    @Override
    public void moveToSave(int uid, Path nativePath, String diskPath, BasicFileInfo fileInfo) throws IOException {
        try(final InputStream is = Files.newInputStream(nativePath)) {
            store(uid, is, diskPath, FileInfo.getFromResource(new PathResource(nativePath), uid, fileInfo.getType()));
            is.close();
            Files.delete(nativePath);
        }
    }

    protected void doCopy(int uid, String source, String target, int targetId, String sourceName, String targetName, Boolean overwrite, int depth, boolean isMove) throws IOException {
        if (depth > maxDepth) {
            throw new JsonException(FileSystemError.DIR_TOO_DEPTH);
        }

        String sourcePath = StringUtils.appendPath(source, sourceName);
        String targetPath = StringUtils.appendPath(target, targetName);


        final Resource sourceResource = getResource(uid, source, sourceName);

        if (sourceResource == null) {

            // 待复制的资源为目录
            if (!exist(targetId, StringUtils.appendPath(target, targetName))) {
                mkdir(targetId, target, targetName);
            }

            int nextDepth = depth + 1;

            for (FileInfo file : lists(uid, StringUtils.appendPath(source, sourceName))) {
                final Resource resource = getResource(uid, sourcePath, file.getName());
                if (!file.isDir()) {

                    // 遇到文件，直接复制
                    // 仅当overwrite为true时才对已存在的同名文件进行覆盖存储
                    if (!exist(targetId, StringUtils.appendPath(targetPath, file.getName())) || overwrite) {
                        store(targetId, resource.getInputStream(), targetPath, FileInfo.getFromResource(resource, targetId, file.getType()));
                    }

                    if (isMove) {
                        delete(uid, sourcePath, Collections.singleton(file.getName()));
                    }
                } else {

                    // 遇到目录，继续递归遍历
                    doCopy(uid, sourcePath, targetPath, targetId, file.getName(), file.getName(), overwrite, nextDepth, isMove);
                }
            }
        } else {
            // 待复制的资源为文件，直接复制
            final FileInfo fileInfo = FileInfo.getFromResource(sourceResource, targetId, FileInfo.TYPE_FILE);
            store(targetId, sourceResource.getInputStream(), target, fileInfo);
        }

        if (isMove) {
            delete(uid, source, Collections.singleton(sourceName));
        }

    }

    /**
     * 检查文件的移动或移动操作的路径参数是否有效
     * @param uid           资源用户ID
     * @param source        数据源所在路径
     * @param target        数据目标所在路径
     * @param targetId      目标位置资源用户ID
     * @param sourceName    数据源文件名
     * @param targetName    目标源文件名
     */
    private void checkCopyOrMove(int uid, String source, String target, int targetId, String sourceName, String targetName) throws IOException {
        final String sourcePath = StringUtils.appendPath(source, sourceName);
        if (!exist(uid, sourcePath)) {
            throw new JsonException(FileSystemError.FILE_NOT_FOUND);
        }

        if (getResource(uid, source, sourceName) == null) {
            if (uid == targetId && PathUtils.isSubDir(sourcePath, StringUtils.appendPath(target, targetName))) {
                throw new JsonException(FileSystemError.TARGET_IS_SUB_DIR);
            }
        }
    }


    @Override
    public void copy(int uid, String source, String target, int targetId, String sourceName, String targetName, Boolean overwrite) throws IOException {
        checkCopyOrMove(uid, source, target, targetId, sourceName, targetName);
        doCopy(uid, source, target, targetId, sourceName, targetName, overwrite, 0, false);

    }

    @Override
    public void move(int uid, String source, String target, String name, boolean overwrite) throws IOException {
        checkCopyOrMove(uid, source, target, uid, name, name);

        doCopy(uid, source, target, uid, name, name, overwrite, 0, true);
    }
}
