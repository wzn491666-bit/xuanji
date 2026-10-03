package com.xuanji.android

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files

class ChatAttachmentsTest {
    @Test fun unknownLengthStreamsAreBoundedAndEmptyFilesRejected() {
        assertEquals(4L, copyAttachmentBounded(ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)), ByteArrayOutputStream(), 4))
        assertTrue(runCatching { copyAttachmentBounded(ByteArrayInputStream(ByteArray(5)), ByteArrayOutputStream(), 4) }.isFailure)
        assertTrue(runCatching { copyAttachmentBounded(ByteArrayInputStream(ByteArray(0)), ByteArrayOutputStream(), 4) }.isFailure)
    }

    @Test fun multipartAttachmentsUseRealBytesAndKeepTheIdempotencyKey() {
        val dir = Files.createTempDirectory("xuanji-attachments").toFile()
        try {
            val image = dir.resolve("a.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val doc = dir.resolve("a.pdf").apply { writeBytes(byteArrayOf(4, 5, 6)) }
            val items = listOf(PromptAttachment("a", "a.png", "image/png", 3, AttachmentKind.IMAGE, image.path),
                PromptAttachment("b", "a.pdf", "application/pdf", 3, AttachmentKind.DOCUMENT, doc.path))
            val buffer = okio.Buffer()
            attachmentPromptBody("inspect", "retry-1", items).writeTo(buffer)
            val payload = Json.parseToJsonElement(buffer.readUtf8()).jsonObject
            assertEquals("retry-1", payload["requestId"]?.jsonPrimitive?.content)
            assertEquals("data:image/png;base64,AQID", payload["images"]?.jsonArray?.single()?.jsonPrimitive?.content)
            assertEquals("data:application/pdf;base64,BAUG", payload["documents"]?.jsonArray?.single()?.jsonObject?.get("dataUrl")?.jsonPrimitive?.content)
            image.writeBytes(byteArrayOf(7))
            assertTrue(runCatching { attachmentPromptBody("inspect", "retry-1", items) }.isFailure)
        } finally { dir.deleteRecursively() }
    }

    @Test fun ordinaryFileMetadataRoundTripsWithoutExposingTheRuntimePathInTheBubble() {
        val attachment = PromptAttachment("f", "script\"\n.py", "text/plain", 10, AttachmentKind.FILE, runtimePath = "/root/.xuanji/attachments/id.py")
        val wire = promptWithFiles("查看代码", listOf(attachment))
        assertTrue(wire.contains("/root/.xuanji/attachments/id.py"))
        val restored = restoreAttachmentMessage(wire, buildJsonObject {})
        assertEquals("查看代码", restored.first)
        assertEquals(attachment.name, restored.second.single().name)
    }

    @Test fun combinedWireSizeIsLimitedEvenWhenIndividualFilesPass() {
        val image = PromptAttachment("i", "a.png", "image/png", 10L * 1024 * 1024, AttachmentKind.IMAGE)
        val doc = PromptAttachment("d", "a.pdf", "application/pdf", 8L * 1024 * 1024, AttachmentKind.DOCUMENT)
        assertTrue(runCatching { validateAttachmentBundle(List(4) { image } + List(4) { doc }) }.isFailure)
        validateAttachmentBundle(List(4) { image })
    }

    @Test fun malformedAttachmentMarkersStayVisibleAndDoNotBreakTheEventStream() {
        val text = "说明\n\n[璇玑附件 JSON]\n{\"message\":{},\"files\":\"bad\"}"
        assertEquals(text, restoreAttachmentMessage(text, buildJsonObject {}).first)
    }
}
