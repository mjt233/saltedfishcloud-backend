package com.saltedfishcloud.ext.hadoop.store;

import com.saltedfishcloud.ext.hadoop.HDFSProperties;
import com.saltedfishcloud.ext.hadoop.HDFSResource;
import com.xiaotao.saltedfishcloud.constant.error.FileSystemError;
import com.xiaotao.saltedfishcloud.entity.po.file.BasicFileInfo;
import com.xiaotao.saltedfishcloud.entity.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.service.file.AbstractStoreService;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import com.xiaotao.saltedfishcloud.validator.FileValidator;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;

public class HDFSStoreService extends AbstractStoreService {
    private final static Resource DEFAULT_AVATAR = new ClassPathResource("/static/static/defaultAvatar.png");

    @Autowired
    private HDFSProperties properties;

    @Autowired
    private FileSystem fs;

    @Override
    public Resource getAvatar(int uid) {
        Path profilePath = new Path(properties.getUserProfileRoot(uid));
        try {
            if (!fs.exists(profilePath)) {
                return DEFAULT_AVATAR;
            }

            final FileStatus[] avatars = fs.listStatus(profilePath, e -> e.getName().contains("avatar"));
            if (avatars != null && avatars.length > 0) {
                return new HDFSResource(fs, avatars[0].getPath());
            } else {
                return DEFAULT_AVATAR;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return DEFAULT_AVATAR;
        }
    }

    @Override
    public void saveAvatar(int uid,Resource resource) throws IOException {
        FileValidator.validateAvatar(resource);
        try (
                final InputStream in = resource.getInputStream();
        ){
            Path profileRoot = new Path(properties.getUserProfileRoot(uid));
            fs.mkdirs(profileRoot);
            final FileStatus[] existAvatars = fs.listStatus(profileRoot, n -> n.getName().contains("avatar"));
            for (FileStatus existAvatar : existAvatars) {
                fs.delete(existAvatar.getPath(), true);
            }
            try(final FSDataOutputStream out = fs.create(
                    new Path(profileRoot + "/avatar." + FileUtils.getSuffix(resource.getFilename())))
            ) {
                StreamUtils.copy(in, out);
            }

        } catch (IOException e) {
            throw new JsonException(500, e.getMessage());
        }
    }

    @Override
    public List<FileInfo> lists(int uid, String path) throws IOException {
        final String target = StringUtils.appendPath(properties.getStoreRoot(uid), path);
        Path targetPath = new Path(target);
        if (!fs.exists(targetPath)) {
            return Collections.emptyList();
        } else {
            List<FileInfo> res = new ArrayList<>();
            for (FileStatus fileStatus : fs.listStatus(targetPath)) {
                FileInfo file;
                if (fileStatus.isDirectory()) {
                    file = new FileInfo(
                            fileStatus.getPath().getName(),
                            -1,
                            FileInfo.TYPE_DIR,
                            PathUtils.getParentPath(path),
                            fileStatus.getModificationTime(),
                            null
                    );
                } else {
                    file = new FileInfo(
                            fileStatus.getPath().getName(),
                            fileStatus.getLen(),
                            FileInfo.TYPE_FILE,
                            PathUtils.getParentPath(path),
                            fileStatus.getModificationTime(),
                            new HDFSResource(fs, fileStatus.getPath())
                    );
                }
                file.setCreatedAt(new Date(System.currentTimeMillis()));
                file.setUid(uid);
                res.add(file);
            }
            return res;
        }
    }

    @Override
    public Resource getResource(int uid, String path, String name) throws IOException {
        Path target = new Path(StringUtils.appendPath(properties.getStoreRoot(uid), path, name));
        if (!fs.exists(target) || fs.getFileStatus(target).isDirectory()) {
            return null;
        }
        return new HDFSResource(fs, target);
    }

    @Override
    public boolean exist(int uid, String path) {
        Path target = new Path(StringUtils.appendPath(properties.getStoreRoot(uid), path));
        try {
            return fs.exists(target);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * @TODO 相同文件并发写入会出问题，需要分布式锁
     */
    @Override
    public void store(int uid, InputStream input, String targetDir, FileInfo fileInfo) throws IOException {
        Path target = new Path(StringUtils.appendPath(properties.getStoreRoot(uid), targetDir, fileInfo.getName()));
        if (fs.exists(target)) {
            if (fs.getFileStatus(target).isDirectory()) {
                throw new JsonException(FileSystemError.RESOURCE_TYPE_NOT_MATCH);
            }
            fs.delete(target, false);
        }
        try(final FSDataOutputStream out = fs.create(target)) {
            StreamUtils.copy(input, out);
        }
        input.close();

    }

//    @Override
//    public void move(int uid, String source, String target, String name, boolean overwrite) throws IOException {
//        String src = StringUtils.appendPath(properties.getStoreRoot(uid), source, name);
//        String dst = StringUtils.appendPath(properties.getStoreRoot(uid), target, name);
//        fs.rename(
//                new Path(src),
//                new Path(dst)
//        );
//    }

    @Override
    public void rename(int uid, String path, String oldName, String newName) throws IOException {
        String base = StringUtils.appendPath(properties.getStoreRoot(uid), path);
        fs.rename(
                new Path(StringUtils.appendPath(base, oldName)),
                new Path(StringUtils.appendPath(base, newName))
        );
    }

    @Override
    public boolean mkdir(int uid, String path, String name) throws IOException {
        Path target = new Path(StringUtils.appendPath(properties.getStoreRoot(uid), path, name));
        if (fs.exists(target) && fs.getFileStatus(target).isFile()) {
            throw new JsonException(FileSystemError.RESOURCE_TYPE_NOT_MATCH);
        }
        return fs.mkdirs(target);
    }

    @Override
    public int delete(String md5) throws IOException {
        throw new UnsupportedOperationException("Hadoop 存储暂未支持根据MD5删除文件");
    }

    @Override
    public long delete(int uid, String path, Collection<String> files) throws IOException {
        int cnt = 0;
        String base = properties.getStoreRoot(uid);
        for (String file : files) {
            Path p = new Path(StringUtils.appendPath(base, path, file));
            fs.delete(p, true);
            cnt++;
        }
        return cnt;
    }
}
