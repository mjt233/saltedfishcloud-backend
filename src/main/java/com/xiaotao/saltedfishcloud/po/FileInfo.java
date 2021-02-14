package com.xiaotao.saltedfishcloud.po;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
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

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FileInfo {
    private String md5;
    public final static int TYPE_DIR = 1;
    public final static int TYPE_FILE = 2;
    private String name;
    private String parent;

    @JsonIgnore
    private String path;
    private String node;
    private Long size;
    private Integer type;
    private Long lastModified;
    private Date created_at;
    private Date updated_at;

    public FileInfo(MultipartFile file) {
        name = file.getOriginalFilename();
        size = file.getSize();
        type = TYPE_FILE;
        lastModified = new Date().getTime();
        originFile2 = file;
    }

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private File originFile;
    @Getter(AccessLevel.NONE)
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

    @JsonIgnore
    public String getFormatSize() {
        if (type == TYPE_DIR) {
            return "-";
        } else {
            return StringUtils.getFormatSize(size);
        }
    }

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
        return StringUtils.getFileSuffix(name);
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
                InputStream is = null;
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
