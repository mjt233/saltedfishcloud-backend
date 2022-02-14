package com.xiaotao.saltedfishcloud.service.file.store;

import com.xiaotao.saltedfishcloud.constant.error.FileSystemError;
import com.xiaotao.saltedfishcloud.entity.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

/**
 * 通用的文件系统复制与移动操作器。
 * 子类只需提供{@link StoreReader}，目录创建与单个文件的复制/移动方法，CopyAndMoveHandler即可实现目录的复制与移动，且支持目录合并与同名文件覆盖控制。
 */
@Slf4j
public abstract class CopyAndMoveHandler {
    private final static int MAX_DEPTH = 128;

    private final StoreReader reader;

    /**
     * 实例化复制移动操作器
     * @param reader    存储读取器
     */
    public CopyAndMoveHandler(StoreReader reader) {
        this.reader = reader;
    }

    /**
     * 复制单个文件
     * @param src   待复制的源文件路径
     * @param dest  粘贴路径
     * @return      复制成功为true，否则为false
     * @throws IOException  任意IO错误
     */
    protected abstract boolean copyFile(String src, String dest) throws IOException;

    /**
     * 移动单个文件
     * @param src   待移动的源文件路径
     * @param dest  粘贴路径
     * @return      移动成功为true，否则为false
     * @throws IOException  任意IO错误
     */
    protected abstract boolean moveFile(String src, String dest) throws IOException;

    /**
     * 文件移动是否需要递归执行。
     * @return 若返回值为true，则通过递归逐个移动文件。若为false，则直接调用{@link CopyAndMoveHandler#moveFile(String, String)}方法进行移动。
     * 使用递归移动时，支持同名目录合并。
     *
     */
    protected abstract boolean isMoveWithRecursion();

    /**
     * 创建单个目录
     * @param path  目录完整路径
     * @return      成功true，否则false
     * @throws IOException  任意IO错误
     */
    protected abstract boolean mkdir(String path) throws IOException;


    /**
     * 判断路径是否为目录
     * @param path  待判断的路径
     * @return      路径为目录则为true，否则为false
     * @throws IOException  任意IO错误
     */
    protected boolean isDirectory(String path) throws IOException {
        final FileInfo fileInfo = reader.getFileInfo(path);
        return fileInfo != null && fileInfo.isDir();
    }

    /**
     * 复制文件，如果复制的是目录，则整个目录复制。复制目录时，若存储系统中已存在dest目录，则进行目录合并，文件是否覆盖由overwrite决定
     * @param src       待复制的文件或目录路径
     * @param dest      粘贴路径
     * @param overwrite 同名文件是否覆盖，true覆盖，false跳过
     * @throws IOException  任意IO错误
     */
    public void copy(String src, String dest, boolean overwrite) throws IOException {
        checkCopyOrMove(src, dest);
        doCopy(src, dest, overwrite, 0, false);
    }

    /**
     * 移动文件或目录。移动目录时，若存储系统中已存在dest目录，则进行目录合并，文件是否覆盖由overwrite决定
     * @param src       待移动的文件或目录路径
     * @param dest      粘贴路径
     * @param overwrite 同名文件是否覆盖，true覆盖，false跳过
     * @throws IOException  任意IO错误
     */
    public void move(String src, String dest, boolean overwrite) throws IOException {
        checkCopyOrMove(src, dest);
        if (isMoveWithRecursion()) {
            doCopy(src, dest, overwrite, 0, true);
        } else {
            moveFile(src, dest);
        }
    }

    /**
     * 复制或移动的执行方法预实现，是该类的核心方法，包含了覆盖控制，递归深度控制和目录合并逻辑
     * @param source        待复制的源文件或目录路径
     * @param target        粘贴为的目标路径
     * @param overwrite     同名文件是否覆盖
     * @param depth         当前递归深度
     * @param isMove        是否为移动操作，为true时执行移动，false时执行复制
     * @throws IOException  任意IO错误
     */
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
     * 检查文件的移动或移动操作的路径参数是否有效。
     * 以下情况视为无效：<br>
     * <ol>
     *     <li>目标路径为源路径的子路径</li>
     *     <li>源路径与目标路径类型不一致</li>
     * </ol>
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
