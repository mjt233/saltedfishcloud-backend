package com.xiaotao.saltedfishcloud.model.po.file;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.xiaotao.saltedfishcloud.model.template.AuditModel;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import lombok.*;
import lombok.experimental.Accessors;
import org.springframework.beans.BeanUtils;
import org.springframework.core.io.InputStreamSource;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.DigestUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.persistence.Entity;
import javax.persistence.Index;
import javax.persistence.Table;
import javax.persistence.Transient;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Date;

@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Accessors(chain = true)
@Table(
        name = "file_table",
        indexes = {
                @Index(name = "file_index", columnList = "node,name,uid", unique = true),
                @Index(name = "md5_index", columnList = "md5"),
                @Index(name = "uid_index", columnList = "uid")
        })
@Entity
public class FileInfo extends AuditModel {
    public final static int TYPE_DIR = 1;
    public final static int TYPE_FILE = 2;

    /**
     * 文件所在目录节点
     */
    private String node;

    /**
     * 文件修改时间
     */
    private Long mtime;

    /**
     * 文件创建时间
     */
    private Long ctime;

    /**
     * 文件名
     */
    private String name;

    /**
     * 文件md5，若记录为目录则表示目录的节点id
     */
    private String md5;


    /**
     * 文件字节大小, 若为-1则表示是个目录
     */
    private Long size;

    /**
     * 挂载id
     */
    private Long mountId;

    /**
     * 文件记录类型
     *
     * @see FileInfo#TYPE_DIR
     * @see FileInfo#TYPE_FILE
     */
    @Transient
    private Integer type;

    /**
     * 是否为外部挂载的文件系统文件
     */
    private Boolean isMount;

    /**
     * 上级目录节点的名称
     */
    @Transient
    private String parent;


    @JsonIgnore
    @Transient
    private transient InputStreamSource streamSource;

    /**
     * 相对用户网盘中，文件所在的目录路径，或在存储系统中的完整路径
     */
    @JsonIgnore
    @Transient
    private String path;

    @JsonIgnore
    public boolean isFile() {
        return size != -1L || (type != null && type == TYPE_FILE);
    }

    public boolean isDir() {
        return !isFile();
    }

    /**
     * 获取文件后缀名，不带点.
     * @return 后缀名
     */
    public String getSuffix() {
        return FileUtils.getSuffix(name);
    }

    /**
     * 从资源接口中创建文件信息
     * @param resource  资源接口
     * @param uid       文件所属用户ID
     * @param type      文件类型，2为文件{@link FileInfo#TYPE_FILE}，1为目录{@link FileInfo#TYPE_DIR}
     * @return          文件信息
     */
    public static FileInfo getFromResource(Resource resource, Long uid, Integer type) throws IOException {
        final FileInfo fileInfo = new FileInfo();
        Date now = new Date();
        fileInfo.setName(resource.getFilename());
        fileInfo.setUid(uid);
        fileInfo.setCreateAt(now);
        fileInfo.setSize(type == FileInfo.TYPE_DIR ? -1 : resource.contentLength());
        fileInfo.setMtime(now.getTime());
        fileInfo.setType(type);
        fileInfo.setStreamSource(resource);
        return fileInfo;
    }

    /**
     * 获取本地文件的文件信息（将自动计算其MD5）
     * @param path  本地文件路径
     * @return  文件信息对象
     */
    public static FileInfo getLocal(String path) {
        try {
            FileInfo fileInfo = getLocal(path, true);
            fileInfo.streamSource = new PathResource(Paths.get(path));
            return fileInfo;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 获取本地文件的文件信息
     * @param path  本地文件路径
     * @param computeMd5 是否计算MD5
     * @return  文件信息对象
     */
    public static FileInfo getLocal(String path, boolean computeMd5) throws IOException {
        FileInfo info = new FileInfo(new File(path));
        if (computeMd5) {
            info.updateMd5();
        }
        return info;
    }

    public FileInfo(MultipartFile file) {
        this(file.getOriginalFilename(), file.getSize(), TYPE_FILE, null, System.currentTimeMillis(), file);
    }

    public FileInfo(File file) {
        this(file.getName(), file.isDirectory() ? -1 : file.length(), file.isDirectory() ? TYPE_DIR : TYPE_FILE, file.getPath(), file.lastModified(), new PathResource(file.getPath()));
        setMtime(file.lastModified());
    }

    public FileInfo(String name, long size, int type, String path, long mtime, InputStreamSource streamSource) {
        this.name = name;
        this.size = size;
        this.type = type;
        this.path = path;
        this.mtime = mtime;
        this.streamSource = streamSource;
        if (type == TYPE_DIR) {
            this.size = -1L;
        }
    }

    /**
     * 更新MD5值，仅当对象通过有参构造方法创建才有效
     */
    public void updateMd5() throws IOException {
        if (isDir()) return;
        if (md5 == null) {
            try(final InputStream is = streamSource.getInputStream()) {
                md5 = DigestUtils.md5DigestAsHex(is);
            }
        }
    }

    /**
     * 从一个现有的文件信息中创建副本
     * @param fileInfo          源对象
     * @param withAuditField    是否复制数据审核字段
     * @return                  新对象
     */
    public static FileInfo createFrom(FileInfo fileInfo, boolean withAuditField) {
        FileInfo newObj = new FileInfo();
        BeanUtils.copyProperties(fileInfo, newObj);
        if (!withAuditField) {
            newObj.setUpdateAt(null);
            newObj.setCreateAt(null);
            newObj.setUid(null);
            newObj.setId(null);
        }
        newObj.setNode(null);
        newObj.setMountId(null);
        newObj.setIsMount(false);
        return newObj;
    }

    /**
     * 将文件的本身固有属性复制到当前文件对象中
     * @param fileInfo  待复制文件
     * @return          复制属性后的自己（用于链式调用）
     */
    public FileInfo copyFrom(FileInfo fileInfo) {
        this.setMd5(fileInfo.getMd5());
        this.setSize(fileInfo.getSize());
        this.setCtime(fileInfo.getCtime());
        this.setMtime(fileInfo.getMtime());
        return this;
    }
}
