package com.sfc.archive.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ArchiveUtilsTest {

    @Test
    void getFilename() {
        assertEquals("name", ArchiveUtils.getFilename("a/name"));
        assertEquals("name", ArchiveUtils.getFilename("a/name/"));
        assertEquals("name", ArchiveUtils.getFilename("name/"));
        assertEquals("name", ArchiveUtils.getFilename("name"));
        assertEquals("c", ArchiveUtils.getFilename("name/a/b/c/"));
        assertEquals("c", ArchiveUtils.getFilename("name/a/b/c"));
        assertEquals("c", ArchiveUtils.getFilename("/name/a/b/c"));
        assertEquals("c", ArchiveUtils.getFilename("/c"));
        assertEquals("c", ArchiveUtils.getFilename("c"));

        assertTrue(ArchiveUtils.isDirectory("/"));
        assertTrue(ArchiveUtils.isDirectory("a/"));
        assertTrue(ArchiveUtils.isDirectory("/nn/a/"));
        assertFalse(ArchiveUtils.isDirectory("/a"));
        assertFalse(ArchiveUtils.isDirectory("/a"));
        assertFalse(ArchiveUtils.isDirectory("a"));
    }
}