package com.xiaotao.saltedfishcloud.po;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import jdk.nashorn.internal.objects.annotations.Getter;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.util.DigestUtils;

import java.io.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FileInfo {
    private String md5;
    public final static int TYPE_DIR = 1;
    public final static int TYPE_FILE = 2;
    private String name;
    private String path;
    private Long size;
    private Integer type;
    private Long lastModified;

    @lombok.Getter(AccessLevel.NONE)
    @lombok.Setter(AccessLevel.NONE)
    private File originFile;
    public FileInfo(File file) {
        originFile = file;
        name = file.getName();
        size = file.length();
        type = file.isDirectory() ? TYPE_DIR : TYPE_FILE;
        path = file.getPath();
        lastModified = file.lastModified();
    }

    public String getFormatSize() {
        if (type == TYPE_DIR) {
            return "-";
        } else {
            return StringUtils.getFormatSize(size);
        }
    }

    /**
     * 获取文件相对于本地公共资源目录的相对路径，
     * @return
     */
    @JsonIgnore
    public String getResourcesRelativePath() {
        return path.substring(DiskConfig.PUBLIC_ROOT.length()).replaceAll("\\\\+","/");
    }

    @Getter
    @JsonIgnore
    public String getPath() {
        return path.replaceAll("\\\\+","/");
    }

//    public String getURLLink() {
//        return StringUtils.linkToURLEncoding(getResourcesRelativePath());
//    }

    /**
     * 获取文件后缀名，不带点.
     * @return
     */
    public String getSuffix() {
        return StringUtils.getFileSuffix(name);
    }

    /**
     * 获取格式化的最后一次修改日期，格式为"yyyy-MM-dd hh:mm"
     * @return
     */
    public String getFormatModified() {
        LocalDateTime localDateTime = LocalDateTime.ofEpochSecond(lastModified / 1000, 0, ZoneOffset.ofHours(8));
        return localDateTime.toLocalDate() + " " + localDateTime.toLocalTime();
    }

    public void computeMd5() {
        if (originFile.isDirectory()) return;
        if (md5 == null) {
            try {
                InputStream is = new FileInputStream(originFile);
                String res = DigestUtils.md5DigestAsHex(is);
                is.close();
                md5 = res;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
