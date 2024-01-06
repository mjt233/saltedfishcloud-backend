package com.xiaotao.saltedfishcloud.service.sync.handler;

import com.xiaotao.saltedfishcloud.dao.mybatis.FileDao;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.FileRecordService;
import com.xiaotao.saltedfishcloud.service.sync.model.FileChangeInfo;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Component
@Slf4j
public class SyncDiffHandlerImpl implements SyncDiffHandler{
    @Resource
    private FileRecordService fileRecordService;
    @Resource
    private FileDao fileDao;

    @Override
    public void handleFileAdd(long uid, Collection<FileInfo> files) throws IOException {
        for (FileInfo fileInfo : files) {
            if (fileInfo.getMd5() == null) {
                fileInfo.updateMd5();
            }
            fileRecordService.addRecord(uid, fileInfo.getName(), fileInfo.getSize(), fileInfo.getMd5(), fileInfo.getPath());
        }
    }

    @Override
    public void handleFileDel(long uid, Collection<FileInfo> files) {
        for (FileInfo file : files) {
            fileDao.deleteRecord(uid, file.getNode(), file.getName());
        }
    }

    @Override
    public void handleFileChange(long uid, Collection<FileChangeInfo> files) throws IOException {
        for (FileChangeInfo changeInfo : files) {
            final FileInfo newFile = changeInfo.newFile;
            fileDao.updateRecord(
                    uid,
                    newFile.getName(),
                    newFile.getNode(),
                    newFile.getSize(),
                    newFile.getMd5()
            );
        }
    }

    @Override
    public void handleDirAdd(long uid, Collection<String> paths) throws IOException {
        for (String e : paths) {
            FileInfo info = new FileInfo();
            int i = e.lastIndexOf('/');
            String path = e.substring(0, i);
            String name = e.substring(i + 1);
            info.setPath(path.length() == 0 ? "/" : path);
            info.setName(name);
            fileRecordService.mkdir(uid, name, path.length() == 0 ? "/" : path);
        }
    }

    @Override
    public void handleDirDel(long uid, Collection<String> paths) throws IOException {
        Set<String> hasDelete = new HashSet<>();
        for(String p : paths ){
            boolean breakFlag = false;
            int index = p.lastIndexOf('/');
            String path = p.substring(0, index);
            String name = p.substring(index + 1);
            String[] node1 = PathUtils.getAllNode(path);
            for (String s : node1) {
                if (hasDelete.contains(s)) {
                    breakFlag = true;
                    break;
                }
            }
            if (breakFlag) {
                continue;
            }
            fileRecordService.deleteRecords(uid, path, Collections.singleton(name));
            hasDelete.add(p);
        }
    }
}
