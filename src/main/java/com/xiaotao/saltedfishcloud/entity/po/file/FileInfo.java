package com.xiaotao.saltedfishcloud.entity.po.file;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import org.springframework.util.DigestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
public class FileInfo extends BasicFileInfo{
    /**
     * 文件所属用户ID
     */
    private Integer uid;
    private String parent;

    @JsonIgnore
    private String path;
    private String node;
    private Long lastModified;
    private Date created_at;
    private Date updated_at;

    /**
     * 获取本地文件的文件信息（将自动计算其MD5）
     * @param path  本地文件路径
     * @return  文件信息对象
     */
    public static FileInfo getLocal(String path) {
        return getLocal(path, true);
    }

    /**
     * 获取本地文件的文件信息
     * @param path  本地文件路径
     * @param computeMd5 是否计算MD5
     * @return  文件信息对象
     */
    public static FileInfo getLocal(String path, boolean computeMd5) {
        FileInfo info = new FileInfo(new File(path));
        if (computeMd5) {
            info.updateMd5();
        }
        return info;
    }

    public FileInfo(MultipartFile file) {
        name = file.getOriginalFilename();
        size = file.getSize();
        type = TYPE_FILE;
        lastModified = new Date().getTime();
        originFile2 = file;
    }

    @Setter(AccessLevel.NONE)
    private File originFile;
    @Setter(AccessLevel.NONE)
    private MultipartFile originFile2;

    public FileInfo(File file) {
        originFile = file;
        name = file.getName();
        size = file.isFile() ? file.length() : -1;
        type = file.isDirectory() ? TYPE_DIR : TYPE_FILE;
        path = file.getPath();
        lastModified = file.lastModified();
    }

    /**
     * 获取格式化的最后一次修改日期，格式为"yyyy-MM-dd hh:mm"
     * @return 日期字符串
     */
    public String getFormatModified() {
        long time = 0;
        if (lastModified != null) {
            time = lastModified;
        } else if (updated_at != null) {
            time = updated_at.getTime();
        } else if (created_at != null) {
            time = created_at.getTime();
        }
        LocalDateTime localDateTime = LocalDateTime.ofEpochSecond(time / 1000, 0, ZoneOffset.ofHours(8));
        return localDateTime.toLocalDate() + " " + localDateTime.toLocalTime();
    }

    public void updateMd5() {
        if (isDir()) return;
        if (md5 == null) {
            try {
                InputStream is;
                if (originFile != null) is = new FileInputStream(originFile);
                else is = originFile2.getInputStream();
                String res = DigestUtils.md5DigestAsHex(is);
                is.close();
                md5 = res;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
