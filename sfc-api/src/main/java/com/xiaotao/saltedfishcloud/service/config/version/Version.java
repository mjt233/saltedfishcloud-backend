package com.xiaotao.saltedfishcloud.service.config.version;

import lombok.Getter; 

/**
 * 版本信息
 */
public final class Version implements Comparable<Version>{
    @Getter
    private int bigVer = 1;
    @Getter
    private int mdVer = 0;
    @Getter
    private int smVer = 0;
    @Getter
    private int bugFixVer = 0;
    @Getter
    private VersionTag tag;

    private String stringCache;

    /**
     * 获取有史以来最早的版本信息
     */
    public static Version getEarliestVersion() {
        return valueOf("1.0.0.0-SNAPSHOT");
    }

    /**
     * 0版本
     */
    private static final Version ZERO_VERSION = Version.valueOf("0.0.0");

    public static Version getZeroVersion() {
        return ZERO_VERSION;
    }

    /**
     * 创建一个版本信息对象
     * @param bigVer    大版本号
     * @param mdVer     中版本号
     * @param smVer     小版本号
     * @param bugFixVer 修订号
     * @param tag       版本类型标签
     */
    public Version(int bigVer, int mdVer, int smVer, int bugFixVer, VersionTag tag) {
        this.bigVer = bigVer;
        this.mdVer = mdVer;
        this.smVer = smVer;
        this.bugFixVer = bugFixVer;
        this.tag = tag;
    }

    /**
     * 从字符串解析为Version版本信息<br>
     * 例子：1.2.3.4-SNAPSHOT,依次为{大版本号}.{中版本号}.{小版本号}.{修正号}-{版本标签}<br>
     * 其中修正号和版本标签是可选的，修正号默认为0，版本标签默认为SNAPSHOT，以下均为合法的版本字符串<br>
     * <ul>
     *  <li>1.2.3.4-SNAPSHOT</li>
     *  <li>1.2.3.4-RELEASE</li>
     *  <li>1.2.3-SNAPSHOT</li>
     *  <li>1.2.3.4</li>
     *  <li>1.2.3</li>
     * </ul>
     * @param version   版本信息字符串
     * @return  Version对象
     */
    public static Version valueOf(String version) {
        try {
            String[] s = version.split("[.\\-]", 5);
            int fixVer = 0;
            VersionTag vt;
            if (s.length == 5) {
                fixVer = Integer.parseInt(s[3]);
                vt = VersionTag.valueOf(s[4]);
            } else {
                char c = s[s.length - 1].charAt(0);
                if (c < '0' || c > '9' ) {
                    vt = VersionTag.valueOf(s[3]);
                } else {
                    // 缺少版本标签类型，默认SNAPSHOT
                    vt = VersionTag.SNAPSHOT;
                    if (s.length == 4) {
                        fixVer = Integer.parseInt(s[s.length - 1]);
                    }
                }
            }
            return new Version(
                    Integer.parseInt(s[0]),
                    Integer.parseInt(s[1]),
                    Integer.parseInt(s[2]),
                    fixVer,
                    vt
            );
        } catch (RuntimeException e) {
            e.printStackTrace();
            throw new IllegalArgumentException("Illegal version format: " + version);
        }
    }

    /**
     * 将版本信息转换为有序整数，版本越高数字越大
     * @return
     */
    public int toInteger() {
        return bigVer * 10000000 + mdVer * 100000 + smVer * 100 + bugFixVer;
    }

    /**
     * 判断当前版本是否小于传入的版本
     * @param o     版本对象
     * @param vl    版本级别
     * @return      若当前版本小于传入的版本，则为true
     */
    public boolean isLessThen(Version o, VersionLevel vl) {
        int curVerVal = toInteger();
        int inVerVal = o.toInteger();
        int coef = 1;
        if (vl == VersionLevel.BIG) {
            coef = 1000000;
        } else if (vl == VersionLevel.MIDDLE) {
            coef = 100000;
        }
        return curVerVal/coef < inVerVal/coef;
    }

    /**
     * 判断当前版本是否小于传入的版本
     * @param o     版本对象
     * @return      若当前版本小于传入的版本，则为true
     */
    public boolean isLessThen(Version o) {
        return isLessThen(o, VersionLevel.SMALL);
    }

    @Override
    public String toString() {
        if (this.stringCache == null) {
            this.stringCache = bigVer + "." + mdVer + "." + smVer + "." + bugFixVer + "-" + tag;
        }
        return this.stringCache;
    }

    /**
     * 将传入的版本对象与当前版本进行大小比较<br>
     *     两者相等，返回0<br>
     *     传入大于当前，返回正数<br>
     *     传入小于当前，返回负数<br>
     * @param o 传入的版本对象
     * @return  比较结果
     */
    @Override
    public int compareTo(Version o) {
        return o.toInteger() - this.toInteger();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Version)) return false;
        Version version = (Version) o;
        return bigVer == version.bigVer &&
                mdVer == version.mdVer &&
                smVer == version.smVer &&
                bugFixVer == version.bugFixVer;
    }

    @Override
    public int hashCode() {
        return this.toString().hashCode();
    }

}
