package com.xiaotao.saltedfishcloud.utils;

import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.config.StoreType;
import com.xiaotao.saltedfishcloud.helper.PathBuilder;
import com.xiaotao.saltedfishcloud.po.file.BasicFileInfo;
import com.xiaotao.saltedfishcloud.po.file.DirCollection;
import com.xiaotao.saltedfishcloud.po.file.FileInfo;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.HashMap;
import java.util.function.Consumer;

@Slf4j
public class FileUtils {
    private static final HashMap<String,String> map = new HashMap<>();
    static {
        // 一般网页资源
        map.put("html", "text/html;charset=utf-8");
        map.put("htm", "text/html;charset=utf-8");
        map.put("js", "application/x-javascript;charset=utf-8");
        map.put("css", "text/css;charset=utf-8");
        map.put("txt", "text/plain;charset=utf-8");
        map.put(".otf" , "application/x-font-otf");

        // 图片
        map.put("gif", "image/gif");
        map.put("jpg", "image/jpeg");
        map.put("jpeg", "image/jpeg");
        map.put("png", "image/png");
        map.put("ico", "image/x-icon");

        // 音乐
        map.put("mp3", "audio/mp3");
        map.put("mp2", "audio/mp2");
        map.put("ogg", "audio/ogg");
        map.put("ape", "audio/ape");
        map.put("flac", "audio/flac");

        // 视频
        map.put("mp4", "video/mp4");

        // 视频
        map.put("pdf", "application/pdf");
    }

    /**
     * 若路径path所在父目录不存在，则创建该path的父目录
     * @param path  路径
     */
    static public void createParentDirectory(String path) throws IOException {
        PathBuilder pathBuilder = new PathBuilder();
        Path target = Paths.get(pathBuilder.append(path).range(-1));
        if (!Files.exists(target)) {
            Files.createDirectories(target);
        } else if (!Files.isDirectory(target)) {
            throw new IOException(target.toString() + "是文件");
        }
    }

    /**
     * 若路径path所在父目录不存在，则创建该path的父目录
     * @param path  路径
     */
    static public void createParentDirectory(Path path) throws IOException {
        createParentDirectory(path.toString());
    }

    /**
     * 通过文件名获取对应文件类型的Content-Type
     * @param name  文件名
     * @return Content-Type结果
     */
    static public String getContentType(String name) {
        name = getSuffix(name);
        String res = map.get(name);
        return res == null ? "application/octet-stream" : res;
    }

    /**
     * 删除一个文件或一个目录及其子目录与文件
     * @param local 本地存储路径
     */
    public static void delete(Path local) throws IOException {
        log.info(local.toString());
        DirCollection dirCollection = scanDir(local);
        Collections.reverse(dirCollection.getDirList());
        dirCollection.getFileList().forEach(File::delete);
        dirCollection.getDirList().forEach(File::delete);
        Files.delete(local);
    }

    /**
     * 搜索遍历目录，取出文件夹下的所有文件和目录
     * @param root 本地文件夹路径
     * @return DirCollection对象
     */
    static public DirCollection scanDir(Path root) throws IOException {
        DirCollection res = new DirCollection();
        Files.walkFileTree(root , new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                res.addFile(file.toFile());
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (!Files.isSameFile(dir, root)) {
                    res.addFile(dir.toFile());
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return res;
    }


    /**
     * 获取文件名中的后缀名，即最后一个字符'.'后面的子字符串，若无后缀名，则返回整个原文件名
     * @author xiaotao
     * @param name  文件名或文件路径
     * @return  后缀名，不带'.'
     */
    public static String getSuffix(String name) {
        String[] split = name.split("\\.");
        if (split.length != 0) {
            return split[split.length - 1].toLowerCase();
        } else {
            return name;
        }
    }

    /**
     * 合并两个本地目录的内容（包括子目录和文件，文件同名将被覆盖）
     * @param source        源目录，合并完成后将被删除
     * @param target        被合并到的位置
     * @param overwrite     是否覆盖已有文件（若为false，源文件和目录将仍被删除）
     */
    public static void mergeDir(String source, String target, boolean overwrite) throws IOException {
        DirCollection sourceCollection = scanDir(Paths.get(source));
        if (!Files.exists(Paths.get(target))) {
            throw new NoSuchFileException(target);
        }

        //  检查子目录和文件同名情况下类型是否一致
        Consumer<File> consumer = file -> {
            Path p = Paths.get(target + "/" + StringUtils.removePrefix(source, file.getPath()));
            if (Files.exists(p)) {
                if (file.isDirectory() != Files.isDirectory(p)) {
                    throw new UnsupportedOperationException("已存在文件与被移动文件类型不一致: " + file.getName());
                }
            }
        };
        sourceCollection.getFileList().forEach(consumer);
        sourceCollection.getDirList().forEach(consumer);

        for (File file : sourceCollection.getDirList()) {
            Path p = Paths.get(target + "/" + StringUtils.removePrefix(source, file.getPath()));
            if (!Files.exists(p)) Files.createDirectory(p);
        }

        for (File file : sourceCollection.getFileList()) {
            Path p = Paths.get(target + "/" + StringUtils.removePrefix(source, file.getPath()));
            if (overwrite) Files.move(Paths.get(file.getPath()), p, StandardCopyOption.REPLACE_EXISTING);
            else file.delete();
            log.debug("move " + file.getPath() + " -> " + p);
        }

        //  删除源文件夹
        Collections.reverse(sourceCollection.getDirList());
        sourceCollection.getDirList().forEach(File::delete);
        Files.delete(Paths.get(source));
    }

    /**
     * 复制文件或目录
     * @param source        被复制的目录/文件所在目录
     * @param target        目的地目录
     * @param sourceName    源文件名
     * @param targetName    目标文件名
     * @param useHardLink   文件是否使用硬链接
     */
    public static void copy(Path source, Path target, String sourceName, String targetName, boolean useHardLink) throws IOException {
        //  判断源与目标是否存在
        if (!Files.exists(source)) {
            throw new NoSuchFileException("资源 \"" + source + "/" + sourceName + "\" 不存在");
        }
        if (!Files.exists(target) || !Files.isDirectory(target)) {
            throw new NoSuchFileException("目标目录 " + target + " 不存在");
        }
        Path sourceFile = Paths.get(source + "/" + sourceName);
        Path targetFile = Paths.get(target + "/" + targetName);
        if (Files.isDirectory(sourceFile)) {
            int sourceLen = sourceFile.toString().length();
            DirCollection dirCollection = FileUtils.scanDir(sourceFile);
            if (!Files.exists(targetFile)) {
                Files.createDirectory(targetFile);
            }
            //  先创建文件夹
            for(File dir: dirCollection.getDirList()) {
                String src = dir.getPath().substring(sourceLen);
                Path dest = Paths.get(target + "/" + targetName + "/" + src);
                log.debug("local filesystem mkdir: " + dest);
                try { Files.createDirectory(dest); } catch (FileAlreadyExistsException ignored) {}
            }

            //  复制文件
            for(File file: dirCollection.getFileList()) {
                String src = file.getPath().substring(sourceLen);
                String dest = target + "/" + targetName + src;
                if (useHardLink) {
                    log.debug("create hard link: " + file + " ==> " + dest);
                    Files.createLink(Paths.get(dest), Paths.get(file.getPath()));
                } else {
                    log.debug("local filesystem copy: " + file + " ==> " + dest);
                    try { Files.copy(Paths.get(file.getPath()), Paths.get(dest), StandardCopyOption.REPLACE_EXISTING); }
                    catch (FileAlreadyExistsException ignored) {}
                }
            }
        } else if (useHardLink) {
            Files.createLink(sourceFile, targetFile);
        } else {
            Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
        }

    }
}
