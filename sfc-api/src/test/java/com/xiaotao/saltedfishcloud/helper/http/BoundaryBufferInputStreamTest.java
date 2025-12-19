package com.xiaotao.saltedfishcloud.helper.http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.StreamUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class BoundaryBufferInputStreamTest {

    @Test
    @DisplayName("测试基本读取与边界识别")
    public void testBoundaryBufferInputStream() throws IOException {
        String caseContent = "12345___8____1111111____a|";
        String boundary = "____";
        String eofBoundary = "|";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(caseContent.getBytes());

        assertThrowsExactly(IllegalArgumentException.class, () -> new BoundaryBufferInputStream(inputStream, 1, boundary.getBytes(), eofBoundary.getBytes()));
        BoundaryBufferInputStream stream = new BoundaryBufferInputStream(inputStream, 8, boundary.getBytes(), eofBoundary.getBytes());

        byte[] r = new byte[6];
        assertEquals(6, stream.read(r, 0, r.length));
        assertArrayEquals("12345_".getBytes(), r);
        assertEquals('_', stream.read());
        assertEquals('_', stream.read());
        assertEquals('8', stream.read());
        assertEquals(-1, stream.read());

        assertTrue(stream.nextBoundary());
        assertEquals(6, stream.read(r, 0, r.length));
        assertArrayEquals("111111".getBytes(), r);
        assertEquals('1', stream.read());
        assertTrue(stream.isAtBoundary());
        assertTrue(stream.nextBoundary());
        assertEquals(1, stream.skip(1));
        assertTrue(stream.isAtBoundary());
        assertTrue(stream.nextBoundary());
        assertTrue(stream.isEof());
    }


    @Test
    @DisplayName("测试空流")
    public void testBoundaryBufferInputStreamEmpty() throws IOException {
        String boundary = "____";
        String eofBoundary = "|";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(new byte[0]);
        BoundaryBufferInputStream stream = new BoundaryBufferInputStream(inputStream, 8, boundary.getBytes(), eofBoundary.getBytes());
        assertEquals(-1, stream.read());
    }

    @Test
    @DisplayName("测试终止边界")
    public void testEof() throws IOException {
        String boundary =    "\r\n----1\r\n";
        String eofBoundary = "\r\n----1--";

        String caseContent = "----1\n666" + boundary + "777\r\n" + eofBoundary;
        ByteArrayInputStream content = new ByteArrayInputStream(caseContent.getBytes());
        BoundaryBufferInputStream stream = new BoundaryBufferInputStream(content, 64 * 1024, boundary.getBytes(), eofBoundary.getBytes());
        assertEquals("----1", stream.readLine());
        assertEquals("666", StreamUtils.copyToString(stream, StandardCharsets.UTF_8));
        assertTrue(stream.nextBoundary());
        assertFalse(stream.isEof());
        assertEquals("777\r\n", StreamUtils.copyToString(stream, StandardCharsets.UTF_8));
        assertTrue(stream.isEof());
        assertTrue(stream.nextBoundary());
        assertTrue(stream.isEof());
        assertFalse(stream.nextBoundary());
    }

    @Test
    @DisplayName("测试流关闭")
    public void testClose() throws IOException {
        String caseContent = "12345___8____1111111____a|";
        String boundary = "____";
        String eofBoundary = "|";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(caseContent.getBytes());
        BoundaryBufferInputStream stream = new BoundaryBufferInputStream(inputStream, 8, boundary.getBytes(), eofBoundary.getBytes());

        assertEquals(3,stream.skip(3));
        assertEquals('4', stream.read());

        stream.close();
        assertEquals(-1, stream.read());
        assertEquals(-1, stream.read(new byte[3], 0, 3));

    }

    @Test
    @DisplayName("测试readLine")
    public void testReadLine() throws IOException {
        String caseContent = "12345\r\n\r\n\n___8____1111111____a|";
        String boundary = "____";
        String eofBoundary = "|";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(caseContent.getBytes());
        BoundaryBufferInputStream stream = new BoundaryBufferInputStream(inputStream, 8, boundary.getBytes(), eofBoundary.getBytes());

        assertEquals('1', stream.read());
        assertEquals("2345", stream.readLine(StandardCharsets.UTF_8));
        assertEquals("", stream.readLine());
        assertEquals("", stream.readLine());
        assertEquals('_', stream.read());
        assertEquals('_', stream.read());
        assertEquals("_8", stream.readLine());
        assertEquals(-1, stream.read());
        assertNull(stream.readLine());
    }

    @Test
    @DisplayName("测试输入流视图约束")
    public void testInputStreamView() throws IOException {

        String caseContent = "12345___8____1111111____a|";
        String boundary = "____";
        String eofBoundary = "|";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(caseContent.getBytes());

        assertThrowsExactly(IllegalArgumentException.class, () -> new BoundaryBufferInputStream(inputStream, 1, boundary.getBytes(), eofBoundary.getBytes()));
        BoundaryBufferInputStream stream = new BoundaryBufferInputStream(inputStream, 8, boundary.getBytes(), eofBoundary.getBytes());

        byte[] r = new byte[6];
        assertEquals(6, stream.read(r, 0, r.length));
        assertArrayEquals("12345_".getBytes(), r);

        InputStream inputStreamView1 = stream.createCurBoundaryInputStreamView();
        assertEquals('_', stream.read());
        assertEquals('_', inputStreamView1.read());
        inputStreamView1.close();
        assertEquals('8', stream.read());
        assertEquals(-1, inputStreamView1.read());
        assertEquals(-1, stream.read());

        assertTrue(stream.nextBoundary());
        InputStream inputStreamView2 = stream.createCurBoundaryInputStreamView();
        assertEquals(6, inputStreamView2.read(r, 0, r.length));
        assertArrayEquals("111111".getBytes(), r);
        assertEquals(-1, inputStreamView1.read());
        assertEquals('1', inputStreamView2.read());
        assertEquals(-1, stream.read());
        assertTrue(stream.isAtBoundary());
        assertTrue(stream.nextBoundary());
        assertEquals(1, stream.skip(1));
        assertTrue(stream.isAtBoundary());
        assertTrue(stream.nextBoundary());
        assertTrue(stream.isEof());
    }
}