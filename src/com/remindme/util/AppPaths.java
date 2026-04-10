package com.remindme.util;


import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class AppPaths {

    private AppPaths() {
    }

    /**
     * 开发环境：
     *   返回项目根目录（也就是 user.dir，通常是 pom.xml 所在目录）
     *
     * 打包后的 app-image 环境：
     *   类/jar 通常位于  根目录/app/
     *   这里返回 app 的上一级，也就是整个程序大文件夹根目录
     */
    public static Path getAppRoot() {
        try {
            Path location = Paths.get(
                    AppPaths.class.getProtectionDomain()
                            .getCodeSource()
                            .getLocation()
                            .toURI()
            );

            String normalized = location.toString().replace("\\", "/");

            // IDEA / Maven 开发环境
            if (normalized.contains("/target/classes")
                    || normalized.contains("/out/production")) {
                return Paths.get(System.getProperty("user.dir"));
            }

            Path parent = location.getParent();
            if (parent != null && parent.getFileName() != null
                    && "app".equalsIgnoreCase(parent.getFileName().toString())) {
                return parent.getParent();
            }

            return parent != null ? parent : Paths.get(System.getProperty("user.dir"));
        } catch (URISyntaxException e) {
            return Paths.get(System.getProperty("user.dir"));
        }
    }

    public static Path getIconsDir() {
        return getAppRoot().resolve("icons");
    }

    public static Path getIconFile(String fileName) {
        return getIconsDir().resolve(fileName);
    }

    public static Path getUserDataDir() {
        return getAppRoot().resolve("usrdata");
    }

    public static Path getDataFile() {
        return getUserDataDir().resolve("data.json");
    }
}