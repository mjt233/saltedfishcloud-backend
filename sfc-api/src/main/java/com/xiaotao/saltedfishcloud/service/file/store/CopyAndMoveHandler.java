package com.xiaotao.saltedfishcloud.service.file.store;

import com.xiaotao.saltedfishcloud.constant.error.FileSystemError;
import com.xiaotao.saltedfishcloud.entity.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

@Slf4j
public abstract class CopyAndMoveHandler {
    private final static int MAX_DEPTH = 128;

    private final StoreReader reader;

    public CopyAndMoveHandler(StoreReader reader) {
        this.reader = reader;
    }

    protected abstract boolean copyFile(String src, String dest) throws IOException;

    protected abstract boolean moveFile(String src, String dest) throws IOException;

    /**
     * 文件移动是否需要递归执行
     */
    protected abstract boolean isMoveWithRecursion();

    protected abstract boolean mkdir(String path) throws IOException;


    protected boolean isDirectory(String path) throws IOException {
        final FileInfo fileInfo = reader.getFileInfo(path);
        return fileInfo != null && fileInfo.isDir();
    }

    public void copy(String src, String dest, boolean overwrite) throws IOException {
        checkCopyOrMove(src, dest);
        doCopy(src, dest, overwrite, 0, false);
    }

    public void move(String src, String dest, boolean overwrite) throws IOException {
        checkCopyOrMove(src, dest);
        if (isMoveWithRecursion()) {
            doCopy(src, dest, overwrite, 0, true);
        } else {
            moveFile(src, dest);
        }
    }


    protected void doCopy(String source, String target, boolean overwrite, int depth, boolean isMove) throws IOException {
        if (depth > MAX_DEPTH) {
            throw new JsonException(FileSystemError.DIR_TOO_DEPTH);
        }


        if (isDirectory(source)) {

            // 待复制的资源为目录
            if (!reader.exist(target)) {
                mkdir(target);
            }

            int nextDepth = depth + 1;

            for (FileInfo file : reader.listFiles(source)) {
                String srcPath = StringUtils.appendPath(source, file.getName());
                String dstPath = StringUtils.appendPath(target, file.getName());
                if (!file.isDir()) {

                    // 遇到文件，直接复制
                    // 仅当overwrite为true时才对已存在的同名文件进行覆盖存储
                    copyFile(srcPath, dstPath);
                    if (!reader.exist(dstPath) || overwrite) {
                        if (isMove) {
                            moveFile(srcPath, dstPath);
                        } else {
                            copyFile(srcPath, dstPath);
                        }
                    }
                } else {

                    // 遇到目录，继续递归遍历
                    doCopy(srcPath, dstPath,overwrite, nextDepth, isMove);
                }
            }
        } else {
            // 待复制的资源为文件，直接复制
            boolean res;
            if (isMove) {
                res = moveFile(source, target);
            } else {
                res = copyFile(source, target);
            }
            if (!res) {
                log.warn("[CopyAndMove]文件{}失败：{} -> {}", isMove ? "移动" : "复制", source, target);
            }
        }

    }

    /**
     * 检查文件的移动或移动操作的路径参数是否有效
     * @param source        数据源所在路径
     * @param target        数据目标所在路径
     */
    private void checkCopyOrMove(String source, String target) throws IOException {
        if (!reader.exist(source)) {
            throw new JsonException(FileSystemError.FILE_NOT_FOUND);
        }

        if (isDirectory(source) && PathUtils.isSubDir(source, target)) {
            throw new JsonException(FileSystemError.TARGET_IS_SUB_DIR);
        }

    }
}
