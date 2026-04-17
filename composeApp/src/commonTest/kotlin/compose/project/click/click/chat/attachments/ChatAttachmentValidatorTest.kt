package compose.project.click.click.chat.attachments

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatAttachmentValidatorTest {

    @Test
    fun validate_rejectsEmptyFileName() {
        val r = ChatAttachmentValidator.validate(fileName = "   ", mimeType = "application/pdf", sizeBytes = 1024)
        assertTrue(r is ChatAttachmentValidator.Result.Invalid)
        assertEquals(ChatAttachmentValidator.Reason.MISSING_FILENAME, r.reason)
    }

    @Test
    fun validate_rejectsZeroBytes() {
        val r = ChatAttachmentValidator.validate("x.pdf", "application/pdf", 0L)
        assertTrue(r is ChatAttachmentValidator.Result.Invalid)
        assertEquals(ChatAttachmentValidator.Reason.EMPTY, r.reason)
    }

    @Test
    fun validate_rejectsNegativeBytes() {
        val r = ChatAttachmentValidator.validate("x.pdf", "application/pdf", -1L)
        assertTrue(r is ChatAttachmentValidator.Result.Invalid)
        assertEquals(ChatAttachmentValidator.Reason.EMPTY, r.reason)
    }

    @Test
    fun validate_acceptsExactlyMaxBytes() {
        val r = ChatAttachmentValidator.validate(
            "x.pdf",
            "application/pdf",
            ChatAttachmentValidator.MAX_ATTACHMENT_BYTES,
        )
        assertEquals(ChatAttachmentValidator.Result.Ok, r)
    }

    @Test
    fun validate_rejectsOneByteOverMax() {
        val r = ChatAttachmentValidator.validate(
            "x.pdf",
            "application/pdf",
            ChatAttachmentValidator.MAX_ATTACHMENT_BYTES + 1L,
        )
        assertTrue(r is ChatAttachmentValidator.Result.Invalid)
        assertEquals(ChatAttachmentValidator.Reason.TOO_LARGE, r.reason)
    }

    @Test
    fun validate_blocksExecutableExtensions() {
        listOf("malware.exe", "tool.APK", "script.sh", "installer.msi", "backdoor.bat").forEach { name ->
            val r = ChatAttachmentValidator.validate(name, "application/octet-stream", 1024)
            assertTrue(r is ChatAttachmentValidator.Result.Invalid, "expected invalid for $name")
            assertEquals(
                ChatAttachmentValidator.Reason.BLOCKED_EXTENSION,
                r.reason,
                "expected BLOCKED_EXTENSION for $name",
            )
        }
    }

    @Test
    fun validate_rejectsUnknownExtension() {
        val r = ChatAttachmentValidator.validate("photo.webp", "image/webp", 1024)
        assertTrue(r is ChatAttachmentValidator.Result.Invalid)
        assertEquals(ChatAttachmentValidator.Reason.DISALLOWED_EXTENSION, r.reason)
    }

    @Test
    fun validate_rejectsFileWithoutExtension() {
        val r = ChatAttachmentValidator.validate("README", "text/plain", 1024)
        assertTrue(r is ChatAttachmentValidator.Result.Invalid)
        assertEquals(ChatAttachmentValidator.Reason.DISALLOWED_EXTENSION, r.reason)
    }

    @Test
    fun validate_rejectsMismatchedMime() {
        val r = ChatAttachmentValidator.validate("safe.pdf", "application/x-sh", 1024)
        assertTrue(r is ChatAttachmentValidator.Result.Invalid)
        assertEquals(ChatAttachmentValidator.Reason.DISALLOWED_MIME, r.reason)
    }

    @Test
    fun validate_allowsCanonicalTypes() {
        val cases = listOf(
            "doc.pdf" to "application/pdf",
            "report.docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "notes.txt" to "text/plain",
            "hero.png" to "image/png",
            "pic.JPG" to "image/jpeg",
            "pic.jpeg" to "image/jpeg",
            "clip.mov" to "video/quicktime",
            "clip.mp4" to "video/mp4",
            "bundle.zip" to "application/zip",
            "data.csv" to "text/csv",
        )
        cases.forEach { (name, mime) ->
            val r = ChatAttachmentValidator.validate(name, mime, 1024L)
            assertEquals(ChatAttachmentValidator.Result.Ok, r, "expected OK for $name / $mime")
        }
    }

    @Test
    fun validate_allowsEmptyMime_whenExtensionIsValid() {
        val r = ChatAttachmentValidator.validate("data.csv", null, 1024L)
        assertEquals(ChatAttachmentValidator.Result.Ok, r)
    }

    @Test
    fun extensionOf_stripsLeadingDotAndLowercases() {
        assertEquals("pdf", ChatAttachmentValidator.extensionOf("File.PDF"))
        assertEquals("tar.gz".substringAfterLast('.'), ChatAttachmentValidator.extensionOf("archive.tar.gz"))
        assertNull(ChatAttachmentValidator.extensionOf("noext"))
        assertNull(ChatAttachmentValidator.extensionOf("trailing."))
    }
}
