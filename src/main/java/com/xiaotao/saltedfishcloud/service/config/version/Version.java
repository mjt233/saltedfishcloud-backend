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
    private VersionTag tag = VersionTag.SNAPSHOT;

    private String stringCache;

    /**
     * 获取有史以来最早的版本信息
     */
    public static Version getEarliestVersion() {
        return valueOf("1.0.0.0-SNAPSHOT");
    }

    public Version(int bigVer, int mdVer, int smVer, int bugFixVer, VersionTag tag) {
        this.bigVer = bigVer;
        this.mdVer = mdVer;
        this.smVer = smVer;
        this.bugFixVer = bugFixVer;
        this.tag = tag;
    }

    public static Version valueOf(String version) {
        try {
            String[] s = version.split("[.\\-]", 5);
            int fixVer = 0;
            VersionTag vt;
            if (s.length == 5) {
                fixVer = Integer.parseInt(s[3]);
                vt = VersionTag.valueOf(s[4]);
            } else {
                vt = VersionTag.valueOf(s[3]);
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

    public int toInteger() {
        return bigVer * 1000000 + mdVer * 100000 + smVer * 100 + bugFixVer;
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
