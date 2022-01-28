package com.xiaotao.saltedfishcloud.utils;

import com.xiaotao.saltedfishcloud.entity.po.file.DirCollection;
import com.xiaotao.saltedfishcloud.entity.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.helper.PathBuilder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@Slf4j
public class FileUtils {
    private static final HashMap<String,String> map = new HashMap<>();
    static {
        // 一般网页资源
        map.put("html", "text/html;charset=utf-8");
        map.put("xml", "text/xml;charset=utf-8");
        map.put("htm", "text/html;charset=utf-8");
        map.put("js", "application/x-javascript;charset=utf-8");
        map.put("json", "application/json;charset=utf-8");
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
        map.put("wav", "audio/wav");
        map.put("midi", "video/midi");
        map.put("mid", "video/midi");

        // 视频
        map.put("mp4", "video/mp4");
        map.put("mkv", "video/x-matroska");
        map.put("mov", "video/quicktime");
        map.put("avi", "video/x-msvideo");


        // 视频
        map.put("pdf", "application/pdf");
    }

    /**
     * 解析文件名，拆分主文件名和拓展名两部分
     * @param name  文件名
     * @return  文件名数组，0为文件名，1为拓展名，若无拓展名，1为null
     */
    static public String[] parseName(String name) {
        int pos = name.lastIndexOf('.');
        if (pos == -1) {
            return new String[]{name, null};
        } else {
            return new String[]{ name.substring(0, pos), name.substring(pos + 1) };
        }
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
        Path parent = path.getParent();
        if (!Files.exists(parent)) {
            Files.createDirectories(parent);
        }
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
    @SuppressWarnings("returnignore")
    public static int delete(@NonNull Path local) throws IOException {
        log.debug("即将删除文件或目录：{}", local);
        AtomicInteger cnt = new AtomicInteger();
        if (Files.isDirectory(local)) {
            Files.walkFileTree(local, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    cnt.incrementAndGet();
                    log.debug("删除文件：{}", file);
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    cnt.incrementAndGet();
                    log.debug("删除目录：{}", dir);
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } else {
            cnt.incrementAndGet();
            log.debug("删除文件：{}", local);
            Files.delete(local);
        }
        log.debug("{}：删除完成", local);
        return cnt.get();
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

            // 若两个文件为同一份文件的链接，则原文件不会被删除。
            // 将目的地同名文件删除以避免该情况
            if (Files.exists(p)) Files.delete(p);
            Files.move(Paths.get(file.getPath()), p, StandardCopyOption.REPLACE_EXISTING);
            log.debug("move {} -> {}", file.getPath(), p);
        }

        //  删除源文件夹
        Collections.reverse(sourceCollection.getDirList());
        sourceCollection.getDirList().forEach(e -> {
            if (!e.delete()) {
                log.debug("删除失败：{}", e.getPath());
            }
        });
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
        if (sourceFile.equals(targetFile)) {
            throw new IllegalStateException("不可原地复制");
        }
        if (Files.isDirectory(sourceFile)) {
            if (sourceName.equals(targetName) && PathUtils.isSubDir(source + "/" + sourceName, target + "/" + targetName)) {
                throw new IllegalArgumentException("目标目录不能是源目录的子目录");
            }

            int sourceLen = sourceFile.toString().length();
            DirCollection dirCollection = FileUtils.scanDir(sourceFile);
            if (!Files.exists(targetFile)) {
                Files.createDirectory(targetFile);
            }
            //  先创建文件夹
            for(File dir: dirCollection.getDirList()) {
                String src = dir.getPath().substring(sourceLen);
                Path dest = Paths.get(target + "/" + targetName + "/" + src);
                log.debug("local filesystem mkdir: {}", dest);
                try { Files.createDirectory(dest); } catch (FileAlreadyExistsException ignored) {}
            }

            //  复制文件
            for(File file: dirCollection.getFileList()) {
                String src = file.getPath().substring(sourceLen);
                String dest = target + "/" + targetName + src;
                if (useHardLink) {
                    log.debug("create hard link: {} ===> {}",file, dest);
                    linkFile(Paths.get(dest), Paths.get(file.getPath()));
                } else {
                    log.debug("local filesystem copy: {} ==> {}", file, dest);
                    try { Files.copy(Paths.get(file.getPath()), Paths.get(dest), StandardCopyOption.REPLACE_EXISTING); }
                    catch (FileAlreadyExistsException ignored) {}
                }
            }
        } else if (useHardLink) {
            log.debug("create hard link: {} ==> {}", sourceFile, targetFile);
            linkFile(targetFile, sourceFile);
        } else {
            log.debug("copy file: {} ==> {}", sourceFile, targetFile);
            Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
        }

    }

    /**
     * 安全地创建文件硬链接，若目标链接已存在，则会先对其进行删除后再创建链接
     * @param link          要创建的链接
     * @param existing      被链接的源文件
     * @throws IOException  出错
     */
    public static void linkFile(Path link, Path existing) throws IOException {
        if (link.equals(existing)) {
            return;
        }
        if (Files.exists(link)) Files.delete(link);
        Files.createLink(link, existing);
    }

    /**
     * 将本地文件系统指定目录下的文件或目录移动到另一个指定目录下，若对文件夹进行操作，且目标位置是已存在文件夹，将对文件夹进行合并
     * @param source        被移动的资源
     * @param target        移动后的目标资源路径
     * @throws UnsupportedOperationException source和target不是同为文件夹或文件
     */
    public static void move(Path source, Path target) throws IOException {

        if (PathUtils.isSubDir(source.toString(), target.toString())) {
            throw new IllegalArgumentException("目标目录不能为源目录的子目录");
        }
        if (Files.exists(target)) {
            if (Files.isDirectory(source) != Files.isDirectory(target)) {
                throw new UnsupportedOperationException("文件类型不一致，无法移动");
            }

            if (Files.isDirectory(source)) {
                // 目录则合并
                FileUtils.mergeDir(source.toString(), target.toString(), true);
            } else {
                Files.delete(target);
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } else {
            Files.move(source, target);
        }
    }

    /**
     * 对某个目录下的文件进行重命名
     * @param path      文件所在目录
     * @param oldName   原名称
     * @param newName   新名称
     * @throws IOException 文件不存在或冲突
     */
    public static void rename(String path, String oldName, String newName) throws IOException {
        File origin = new File(path + "/" + oldName);
        File dist = new File(path + "/" + newName);
        if (!origin.exists()) {
            throw new IOException("原文件不存在");
        }
        if (dist.exists()) {
            throw new IOException("文件名" + newName + "冲突");
        }
        if (!origin.renameTo(dist)) {
            throw new IOException("移动失败");
        }
    }



    /**
     * 通过一个本地路径获取获取该路径下的所有文件列表并区分文件与目录
     * 若路径不存在则抛出异常
     * 若路径指向一个文件则返回null
     * 若路径指向一个目录则返回一个集合，数组下标0为目录，1为文件
     * @param localPath 本地文件夹路径
     * @return 一个List数组，数组下标0为目录，1为文件，或null
     * @throws FileNotFoundException 路径不存在
     */
    public Collection<? extends FileInfo>[] getFileList(String localPath) throws FileNotFoundException {
        File file = new File(localPath);
        return getFileList(file);
    }
    /**
     * 通过一个本地路径获取获取该路径下的所有文件列表并区分文件与目录
     * 若路径不存在则抛出异常
     * 若路径指向一个文件则返回null
     * 若路径指向一个目录则返回一个List数组，数组下标0为目录，1为文件
     * @throws FileNotFoundException 路径不存在
     * @param file 本地文件夹路径
     * @return 一个List数组，数组下标0为目录，1为文件，或null
     */
    @SuppressWarnings("unchecked")
    public Collection<? extends FileInfo>[] getFileList(File file) throws FileNotFoundException {
        if (!file.exists()) {
            throw new FileNotFoundException();
        }
        if (file.isFile()) {
            return null;
        }
        List<FileInfo> dirs = new LinkedList<>();
        List<FileInfo> files = new LinkedList<>();
        try {
            for (File listFile : Objects.requireNonNull(file.listFiles())) {
                if (listFile.isDirectory()) {
                    dirs.add(new FileInfo(listFile));
                } else {
                    files.add(new FileInfo(listFile));
                }
            }
        } catch (NullPointerException e) {
            // do nothing
        }
        return new List[]{dirs, files};
    }


}
