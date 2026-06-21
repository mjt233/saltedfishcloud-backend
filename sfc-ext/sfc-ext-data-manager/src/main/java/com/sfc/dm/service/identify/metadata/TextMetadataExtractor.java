package com.sfc.dm.service.identify.metadata;

import com.sfc.dm.service.identify.util.EncodingDetector;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * 纯文本文件元数据提取器，本地计算编码、行数、文件大小、编程语言
 */
@Slf4j
public class TextMetadataExtractor implements FileMetadataExtractor {
    private static final Map<String, String> EXT_TO_LANGUAGE = Map.ofEntries(
            Map.entry(".java", "Java"),
            Map.entry(".py", "Python"),
            Map.entry(".js", "JavaScript"),
            Map.entry(".ts", "TypeScript"),
            Map.entry(".jsx", "JavaScript (JSX)"),
            Map.entry(".tsx", "TypeScript (TSX)"),
            Map.entry(".go", "Go"),
            Map.entry(".rs", "Rust"),
            Map.entry(".rb", "Ruby"),
            Map.entry(".php", "PHP"),
            Map.entry(".c", "C"),
            Map.entry(".cpp", "C++"),
            Map.entry(".h", "C/C++ Header"),
            Map.entry(".hpp", "C++ Header"),
            Map.entry(".cs", "C#"),
            Map.entry(".swift", "Swift"),
            Map.entry(".kt", "Kotlin"),
            Map.entry(".scala", "Scala"),
            Map.entry(".lua", "Lua"),
            Map.entry(".r", "R"),
            Map.entry(".m", "Objective-C"),
            Map.entry(".pl", "Perl"),
            Map.entry(".pm", "Perl Module"),
            Map.entry(".sh", "Shell"),
            Map.entry(".bash", "Bash"),
            Map.entry(".zsh", "Zsh"),
            Map.entry(".fish", "Fish"),
            Map.entry(".bat", "Batch"),
            Map.entry(".cmd", "Batch"),
            Map.entry(".ps1", "PowerShell"),
            Map.entry(".sql", "SQL"),
            Map.entry(".groovy", "Groovy"),
            Map.entry(".dart", "Dart"),
            Map.entry(".zig", "Zig"),
            Map.entry(".nim", "Nim"),
            Map.entry(".ex", "Elixir"),
            Map.entry(".exs", "Elixir"),
            Map.entry(".erl", "Erlang"),
            Map.entry(".hs", "Haskell"),
            Map.entry(".ml", "OCaml"),
            Map.entry(".fs", "F#"),
            Map.entry(".fsx", "F#"),
            Map.entry(".clj", "Clojure"),
            Map.entry(".lisp", "Lisp"),
            Map.entry(".el", "Emacs Lisp"),
            Map.entry(".asm", "Assembly"),
            Map.entry(".s", "Assembly"),
            Map.entry(".S", "Assembly"),
            Map.entry(".vue", "Vue"),
            Map.entry(".svelte", "Svelte")
    );

    @Override
    public Map<String, String> extract(File file, String mimeType) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("fileSize", String.valueOf(file.length()));
        try {
            String encoding = EncodingDetector.detect(file);
            metadata.put("encoding", encoding);
        } catch (Exception e) {
            log.debug("编码检测失败: {}", file.getName(), e);
        }
        try {
            int lineCount = countLines(file);
            metadata.put("lineCount", String.valueOf(lineCount));
        } catch (Exception e) {
            log.debug("行数统计失败: {}", file.getName(), e);
        }
        String ext = getExtensionWithDot(file.getName().toLowerCase());
        if (ext != null) {
            String language = EXT_TO_LANGUAGE.get(ext);
            if (language != null) {
                metadata.put("language", language);
            }
        }
        return metadata;
    }

    private String getExtensionWithDot(String fileName) {
        if (fileName.startsWith(".")) return fileName;
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) return fileName.substring(dotIndex);
        return null;
    }

    private int countLines(File file) throws IOException {
        int lines = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            while (reader.readLine() != null) lines++;
        }
        return lines;
    }
}
