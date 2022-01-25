package com.xiaotao.saltedfishcloud.ext.hadoop.store;

import com.xiaotao.saltedfishcloud.constant.error.FileSystemError;
import com.xiaotao.saltedfishcloud.entity.po.file.BasicFileInfo;
import com.xiaotao.saltedfishcloud.entity.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.ext.hadoop.HDFSFactory;
import com.xiaotao.saltedfishcloud.ext.hadoop.HDFSResource;
import com.xiaotao.saltedfishcloud.ext.hadoop.HadoopStoreProperties;
import com.xiaotao.saltedfishcloud.service.file.StoreService;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import com.xiaotao.saltedfishcloud.validator.FileValidator;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;

@Service
public class HadoopStoreService implements StoreService {
    private final static Resource DEFAULT_AVATAR = new ClassPathResource("/static/static/defaultAvatar.png");


    @Autowired
    private HDFSFactory hdfsFactory;

    @Autowired
    private HadoopStoreProperties properties;

    @Autowired
    private FileSystem fs;

    @Override
    public Resource getAvatar(int uid) {
        return DEFAULT_AVATAR;
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
                fs.delete(new Path(profileRoot + "/" + existAvatar), true);
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
                FileInfo file = new FileInfo();
                file.setSize(fileStatus.isDirectory() ? -1 : file.getSize());
                file.setName(fileStatus.getPath().getName());
                file.setCreated_at(new Date(fileStatus.getModificationTime()));
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

    @Override
    public void moveToSave(int uid, java.nio.file.Path nativePath, String diskPath, BasicFileInfo fileInfo) throws IOException {
        String dir = StringUtils.appendPath(properties.getStoreRoot(uid), diskPath);
        Path targetDir = new Path(dir);
        Path target = new Path(StringUtils.appendPath(dir, fileInfo.getName()));
        fs.mkdirs(targetDir);
        try(final InputStream in = Files.newInputStream(nativePath);
            final FSDataOutputStream out = fs.create(target);
        ) {
            StreamUtils.copy(in, out);
        }
        Files.delete(nativePath);
    }

    @Override
    public void copy(int uid, String source, String target, int targetId, String sourceName, String targetName, Boolean overwrite) throws IOException {
        throw new UnsupportedOperationException("Hadoop 存储暂不支持复制功能");
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

    @Override
    public void move(int uid, String source, String target, String name, boolean overwrite) throws IOException {
        String src = StringUtils.appendPath(properties.getStoreRoot(uid), source, name);
        String dst = StringUtils.appendPath(properties.getStoreRoot(uid), target, name);
        fs.rename(
                new Path(src),
                new Path(dst)
        );
    }

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
