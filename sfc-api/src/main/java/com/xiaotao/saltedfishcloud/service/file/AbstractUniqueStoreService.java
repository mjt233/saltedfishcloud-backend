package com.xiaotao.saltedfishcloud.service.file;

import com.xiaotao.saltedfishcloud.entity.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.store.DirectRawStoreHandler;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;

/**
 * 唯一存储服务的抽象模板类，一般由{@link AbstractRawStoreService}内部进行实例化。<br>
 * 提供相同文件仅存一份的能力。原理是在特定目录下，利用文件md5组织文件结构来集中存储所有文件<br>
 * <br>
 * 因仅负责集中式的数据仓库的维护，不存在用户网盘文件组织结构的数据，所以不支持列出用户文件列表，判断文件是否存在，按路径删除，复制，移动，重命名等操作，也因此无法参与系统的文件记录同步机制。
 * 但这些操作对于唯一存储服务而言这些是不必要的行为，用户网盘文件组织结构全权交由文件记录服务接口组
 * {@link FileRecordService},<br>
 * {@link com.xiaotao.saltedfishcloud.dao.mybatis.FileDao},<br>
 * {@link com.xiaotao.saltedfishcloud.service.node.NodeService},<br>
 * {@link com.xiaotao.saltedfishcloud.dao.mybatis.NodeDao}<br>
 * 提供。<br>
 * 上述4个接口后续将由{@link FileRecordService}全部集成
 *
 */
@Slf4j
public abstract class AbstractUniqueStoreService extends AbstractRawStoreService {
    protected FileResourceMd5Resolver md5Resolver;
    protected StoreService rawStoreService;
    private static final String LOG_TITLE = "Store-Unique";

    public AbstractUniqueStoreService(
            DirectRawStoreHandler handler,
            FileResourceMd5Resolver md5Resolver,
            StoreService rawStoreService
    ) {
        super(handler, md5Resolver);

        this.md5Resolver = md5Resolver;
        this.rawStoreService = rawStoreService;
    }

    @Override
    public void clear() throws IOException {
        handler.delete(StringUtils.appendPath(getStoreRoot(), "repo"));
    }

    /**
     * 判断输入的MD5是否为有效的MD5字符串
     * @param md5   MD5字符串
     * @return      有效为true，无效为false
     */
    private boolean isValidMd5(String md5) {
        if (md5 == null || md5.length() != 32) {
            return false;
        }
        final char[] chars = md5.toCharArray();
        char ch;
        for (int i = 0; i < 32; i++) {
            ch = chars[i];

            if ( !((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <='9'))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 获取Md5资源仓库路径的结尾部分<br>
     * 例如输入：qazwsxedcrfvtgbyhnujmikolp123456gg，则输出qa/zw/qazwsxedcrfvtgbyhnujmikolp123456gg
     * @param md5   文件MD5
     * @return  文件路径末尾部分，开头不带“/”
     */
    private String getMd5PathEnd(String md5) {
        if (!isValidMd5(md5)) {
            throw new IllegalArgumentException(md5 + "不是有效的md5");
        }

        return md5.substring(0, 2) +
                '/' +
                md5.substring(2, 4) +
                '/' +
                md5;
    }

    /**
     * 获取MD5文件资源的存储服务路径
     * @param md5   文件MD5
     * @return      存储服务绝对路径
     */
    private String getMd5ResourcePath(String md5) {
        return StringUtils.appendPath(getRepoRoot(), getMd5PathEnd(md5));
    }

    @Override
    public void store(int uid, InputStream input, String targetDir, FileInfo fileInfo) throws IOException {
        String md5 = fileInfo.getMd5();
        if (md5 == null) {
            fileInfo.updateMd5();
            md5 = fileInfo.getMd5();
        }
        final String path = StringUtils.appendPath(getRepoRoot(), getMd5PathEnd(md5));
        final String pathParent = PathUtils.getParentPath(path);
        if (!handler.exist(pathParent)) {
            handler.mkdirs(pathParent);
        } else if (handler.exist(path)) {
            log.debug("[{}]文件重复命中：{}，保存路径：{}", LOG_TITLE, fileInfo.getName(), path);
            return;
        }

        log.debug("[{}]存储新文件：{}，保存路径：{}", LOG_TITLE, fileInfo.getName(), path);
        handler.store(path, input);

    }

    @Override
    public long delete(int uid, String path, Collection<String> files) throws IOException {
        int cnt = 0;
        for (String file : files) {
            final String md5 = md5Resolver.getResourceMd5(uid, StringUtils.appendPath(path, file));
            if (!md5Resolver.hasRef(md5)) {
                delete(md5);
                final String storePath = getMd5ResourcePath(md5);
                log.debug("[{}]文件引用丢失，删除文件：{}", LOG_TITLE, storePath);
                handler.delete(storePath);
            }
        }
        return cnt;
    }

    @Override
    public boolean isMoveWithRecursion() {
        return false;
    }

    @Override
    public boolean isUnique() {
        return true;
    }

    @Override
    public boolean canBrowse() {
        return false;
    }

    @Override
    public void rename(int uid, String path, String oldName, String newName) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean mkdir(int uid, String path, String name) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<FileInfo> lists(int uid, String path) throws IOException {
        throw new UnsupportedOperationException("不支持lists");
    }

    @Override
    public Resource getResource(int uid, String path, String name) throws IOException {
        final String md5 = md5Resolver.getResourceMd5(uid, StringUtils.appendPath(path, name));
        if (md5 == null) {
            return null;
        }

        final String resourcePath = getMd5ResourcePath(md5);
        log.debug("[{}]读取Unique资源路径：{}", LOG_TITLE, resourcePath);
        return handler.getResource(resourcePath);
    }

    @Override
    public boolean exist(int uid, String path) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void copy(int uid, String source, String target, int targetId, String sourceName, String targetName, boolean overwrite) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void move(int uid, String source, String target, String name, boolean overwrite) {
        throw new UnsupportedOperationException();
    }

    @Override
    public StoreService getRawStoreService() {
        return rawStoreService;
    }

    @Override
    public StoreService getUniqueStoreService() {
        return this;
    }

    @Override
    public int delete(String md5) throws IOException {
        final String path = getMd5ResourcePath(md5);
        final int res = handler.delete(path) ? 1 : 0;
        final String parent1 = PathUtils.getParentPath(path);

        // 文件删除后若目录为空则连同目录删除
        if (handler.isEmptyDirectory(parent1)) {
            handler.delete(parent1);
            final String parent2 = PathUtils.getParentPath(parent1);
            if (handler.isEmptyDirectory(parent2)) {
                handler.delete(parent2);
            }
        }
        return res;
    }
}
