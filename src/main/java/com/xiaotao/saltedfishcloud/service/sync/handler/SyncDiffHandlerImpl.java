package com.xiaotao.saltedfishcloud.service.sync.handler;

import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.config.StoreType;
import com.xiaotao.saltedfishcloud.dao.FileDao;
import com.xiaotao.saltedfishcloud.po.User;
import com.xiaotao.saltedfishcloud.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.FileRecordService;
import com.xiaotao.saltedfishcloud.service.file.FileService;
import com.xiaotao.saltedfishcloud.service.file.StoreService;
import com.xiaotao.saltedfishcloud.service.node.NodeService;
import com.xiaotao.saltedfishcloud.service.sync.model.FileChangeInfo;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.nio.file.NoSuchFileException;
import java.util.*;

@Component
@Slf4j
public class SyncDiffHandlerImpl implements SyncDiffHandler{
    @Resource
    private FileService fileService;
    @Resource
    private FileRecordService fileRecordService;
    @Resource
    private StoreService storeService;
    @Resource
    private FileDao fileDao;
    @Resource
    private NodeService nodeService;

    @Override
    public void handleFileAdd(User user, Collection<FileInfo> files) throws Exception {
        int uid = user.getId();
        for (FileInfo fileInfo : files) {
            fileInfo.updateMd5();
            storeService.moveToSave(uid, fileInfo.getOriginFile().toPath(), fileInfo.getPath(), fileInfo);
            if (fileRecordService.addRecord(uid, fileInfo.getName(), fileInfo.getSize(), fileInfo.getMd5(), fileInfo.getPath()) <= 0) {
                log.error("信息添加失败：" + fileInfo.getPath() + "/" + fileInfo.getName() + " MD5:" + fileInfo.getMd5());
            }
        }
    }

    @Override
    public void handleFileDel(User user, Collection<FileInfo> files) throws Exception {
        int uid = user.getId();
        for (FileInfo file : files) {
            fileDao.deleteRecord(uid, file.getNode(), file.getName());
        }
    }

    @Override
    public void handleFileChange(User user, Collection<FileChangeInfo> files) throws Exception {
        int uid = user.getId();
        for (FileChangeInfo changeInfo : files) {
            FileInfo newFile = changeInfo.newFile;
            newFile.updateMd5();
            if (DiskConfig.STORE_TYPE == StoreType.UNIQUE) {
                fileService.moveToSaveFile(
                        uid,
                        newFile.getOriginFile().toPath(),
                        newFile.getPath(),
                        newFile
                );
                FileInfo oldFile = changeInfo.oldFile;
                List<FileInfo> list = fileDao.getFilesByMD5(oldFile.getMd5(), 1);
                if (list.size() == 0) {
                    log.debug("File no longer referenced: {}", oldFile.getMd5());
                    try {
                        storeService.delete(oldFile.getMd5());
                    } catch (NoSuchFileException e) {
                        log.warn("Not found md5 file : {}", e.getMessage());
                    }
                }
            } else {
                fileDao.updateRecord(
                        uid,
                        newFile.getName(),
                        nodeService.getLastNodeInfoByPath(uid, newFile.getPath()).getId(),
                        newFile.getSize(),
                        newFile.getMd5()
                );
            }
        }
    }

    @Override
    public void handleDirAdd(User user, Collection<String> paths) throws Exception {
        int uid = user.getId();
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
    public void handleDirDel(User user, Collection<String> paths) throws Exception {
        Set<String> hasDelete = new HashSet<>();
        int uid = user.getId();
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
