package com.sfc.dm.service.identify.metadata;

import com.sfc.dm.service.identify.tika.TikaServerManager;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 图片文件元数据提取器，通过 Tika 提取宽高、拍摄设备、GPS 位置，从 MIME 派生格式
 */
@Slf4j
public class ImageMetadataExtractor implements FileMetadataExtractor {
    private final TikaServerManager tikaServerManager;

    public ImageMetadataExtractor(TikaServerManager tikaServerManager) {
        this.tikaServerManager = tikaServerManager;
    }

    @Override
    public Map<String, String> extract(File file, String mimeType) {
        Map<String, String> metadata = new HashMap<>();

        if (tikaServerManager != null) {
            Map<String, Object> raw = tikaServerManager.extractMetadata(file, Set.of(
                    "tiff:ImageWidth", "tiff:ImageLength",
                    "tiff:Make", "tiff:Model",
                    "GPS Latitude", "GPS Longitude"
            ));
            if (raw != null) {
                if (raw.containsKey("tiff:ImageWidth")) metadata.put("width", String.valueOf(raw.get("tiff:ImageWidth")));
                if (raw.containsKey("tiff:ImageLength")) metadata.put("height", String.valueOf(raw.get("tiff:ImageLength")));
                String make = raw.get("tiff:Make") != null ? String.valueOf(raw.get("tiff:Make")) : null;
                String model = raw.get("tiff:Model") != null ? String.valueOf(raw.get("tiff:Model")) : null;
                if (model != null) {
                    metadata.put("cameraModel", (make != null && !model.contains(make)) ? make + " " + model : model);
                }
                String lat = raw.get("GPS Latitude") != null ? String.valueOf(raw.get("GPS Latitude")) : null;
                String lng = raw.get("GPS Longitude") != null ? String.valueOf(raw.get("GPS Longitude")) : null;
                if (lat != null && lng != null) {
                    metadata.put("gpsLocation", lat + ", " + lng);
                }
            }
        }

        String format = deriveFormatFromMime(mimeType);
        if (format != null) metadata.put("format", format);

        return metadata.isEmpty() ? null : metadata;
    }

    private String deriveFormatFromMime(String mimeType) {
        if (mimeType == null) return null;
        return switch (mimeType) {
            case "image/jpeg" -> "JPEG";
            case "image/png" -> "PNG";
            case "image/gif" -> "GIF";
            case "image/bmp" -> "BMP";
            case "image/webp" -> "WebP";
            case "image/tiff" -> "TIFF";
            case "image/heic" -> "HEIC";
            case "image/heif" -> "HEIF";
            case "image/avif" -> "AVIF";
            case "image/jxl" -> "JXL";
            case "image/apng" -> "APNG";
            case "image/svg+xml" -> "SVG";
            case "image/x-icon" -> "ICO";
            default -> null;
        };
    }
}
