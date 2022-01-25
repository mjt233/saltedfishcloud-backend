package com.xiaotao.saltedfishcloud.ext.store;

import com.xiaotao.saltedfishcloud.entity.po.file.BasicFileInfo;
import com.xiaotao.saltedfishcloud.entity.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.StoreService;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

public class HadoopStoreService implements StoreService {
    @Override
    public Resource getAvatar(int uid) {
        return null;
    }

    @Override
    public void saveAvatar(int uid, Resource resource) throws IOException {

    }

    @Override
    public List<FileInfo> lists(int uid, String path) throws IOException {
        return null;
    }

    @Override
    public Resource getResource(int uid, String path, String name) {
        return null;
    }

    @Override
    public boolean exist(int uid, String path) {
        return false;
    }

    @Override
    public void moveToSave(int uid, Path nativePath, String diskPath, BasicFileInfo fileInfo) throws IOException {

    }

    @Override
    public void copy(int uid, String source, String target, int targetId, String sourceName, String targetName, Boolean overwrite) throws IOException {

    }

    @Override
    public void store(int uid, InputStream input, String targetDir, FileInfo fileInfo) throws IOException {

    }

    @Override
    public void move(int uid, String source, String target, String name, boolean overwrite) throws IOException {

    }

    @Override
    public void rename(int uid, String path, String oldName, String newName) throws IOException {

    }

    @Override
    public boolean mkdir(int uid, String path, String name) throws IOException {
        return false;
    }

    @Override
    public int delete(String md5) throws IOException {
        return 0;
    }

    @Override
    public long delete(int uid, String path, Collection<String> files) throws IOException {
        return 0;
    }
}
