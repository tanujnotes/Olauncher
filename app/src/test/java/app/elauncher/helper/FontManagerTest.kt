package app.elauncher.helper

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.isNull
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.`when`
import java.io.ByteArrayInputStream
import java.io.File

/**
 * [android.graphics.Typeface] is a framework class that isn't functional in a plain JVM unit
 * test (this project has no Robolectric): calling `Typeface.createFromFile` here throws
 * Android's "not mocked" stub exception rather than actually parsing a font. So these tests
 * verify what's observable without it: the file copy/validation logic in [FontManager
 * .handlePickedFont], and the exists-check/delete side effects of [FontManager
 * .loadCustomTypeface] and [FontManager.clearCustomFont]. The "corrupt file" test below
 * exercises the catch-and-delete path via that same stub exception, which lands in the same
 * catch block a real parse failure would. Confirming `loadCustomTypeface` actually returns a
 * usable, non-null [android.graphics.Typeface] for a valid font is left to manual on-device
 * verification when this is wired up in later steps.
 */
class FontManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun buildContext(resolver: ContentResolver): Context {
        val context = mock(Context::class.java)
        `when`(context.filesDir).thenReturn(tempFolder.root)
        `when`(context.contentResolver).thenReturn(resolver)
        return context
    }

    private fun buildResolver(displayName: String?, inputStream: ByteArrayInputStream?): ContentResolver {
        val resolver = mock(ContentResolver::class.java)
        val cursor = mock(Cursor::class.java)
        `when`(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)).thenReturn(0)
        `when`(cursor.moveToFirst()).thenReturn(displayName != null)
        `when`(cursor.getString(0)).thenReturn(displayName)
        `when`(
            resolver.query(any(Uri::class.java), any(), isNull(), isNull(), isNull()),
        ).thenReturn(cursor)
        `when`(resolver.openInputStream(any(Uri::class.java))).thenReturn(inputStream)
        return resolver
    }

    private fun customFontFile(): File = File(tempFolder.root, "custom_font.ttf")

    @Test
    fun `handlePickedFont copies bytes into internal storage for a valid ttf`() {
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        val resolver = buildResolver("myfont.ttf", ByteArrayInputStream(bytes))
        val context = buildContext(resolver)
        val uri = mock(Uri::class.java)

        val result = FontManager.handlePickedFont(context, uri)

        assertTrue(result)
        val copied = customFontFile()
        assertTrue(copied.exists())
        assertTrue(bytes.contentEquals(copied.readBytes()))
    }

    @Test
    fun `handlePickedFont accepts otf extension case-insensitively`() {
        val bytes = byteArrayOf(9, 9, 9)
        val resolver = buildResolver("MyFont.OTF", ByteArrayInputStream(bytes))
        val context = buildContext(resolver)
        val uri = mock(Uri::class.java)

        val result = FontManager.handlePickedFont(context, uri)

        assertTrue(result)
        assertTrue(bytes.contentEquals(customFontFile().readBytes()))
    }

    @Test
    fun `handlePickedFont rejects a non-font file by extension and does not write a file`() {
        val resolver = buildResolver("document.pdf", ByteArrayInputStream(byteArrayOf(1)))
        val context = buildContext(resolver)
        val uri = mock(Uri::class.java)

        val result = FontManager.handlePickedFont(context, uri)

        assertFalse(result)
        assertFalse(customFontFile().exists())
    }

    @Test
    fun `handlePickedFont returns false when display name cannot be resolved`() {
        val resolver = buildResolver(null, ByteArrayInputStream(byteArrayOf(1)))
        val context = buildContext(resolver)
        val uri = mock(Uri::class.java)
        `when`(uri.lastPathSegment).thenReturn(null)

        val result = FontManager.handlePickedFont(context, uri)

        assertFalse(result)
        assertFalse(customFontFile().exists())
    }

    @Test
    fun `handlePickedFont returns false when the input stream cannot be opened`() {
        val resolver = buildResolver("myfont.ttf", null)
        val context = buildContext(resolver)
        val uri = mock(Uri::class.java)

        val result = FontManager.handlePickedFont(context, uri)

        assertFalse(result)
    }

    @Test
    fun `handlePickedFont overwrites a previously picked font, single fixed slot`() {
        val resolver1 = buildResolver("first.ttf", ByteArrayInputStream(byteArrayOf(1, 1)))
        val context1 = buildContext(resolver1)
        FontManager.handlePickedFont(context1, mock(Uri::class.java))
        assertTrue(byteArrayOf(1, 1).contentEquals(customFontFile().readBytes()))

        val resolver2 = buildResolver("second.ttf", ByteArrayInputStream(byteArrayOf(2, 2, 2)))
        val context2 = buildContext(resolver2)
        FontManager.handlePickedFont(context2, mock(Uri::class.java))

        // Still a single file at the fixed filename, now holding the second font's bytes.
        assertEquals(1, tempFolder.root.listFiles()?.size)
        assertTrue(byteArrayOf(2, 2, 2).contentEquals(customFontFile().readBytes()))
    }

    @Test
    fun `loadCustomTypeface returns null when no font has been picked yet`() {
        val context = buildContext(mock(ContentResolver::class.java))

        assertNull(FontManager.loadCustomTypeface(context))
        assertFalse(customFontFile().exists())
    }

    @Test
    fun `loadCustomTypeface deletes an unparsable stored file and returns null`() {
        val context = buildContext(mock(ContentResolver::class.java))
        customFontFile().writeBytes(byteArrayOf(0, 1, 2))
        assertTrue(customFontFile().exists())

        // Typeface.createFromFile is a framework call that isn't functional in a plain JVM
        // test; it throws here rather than actually attempting to parse the bytes, landing in
        // the same catch-and-delete path a real corrupt/invalid font file would. android.util
        // .Log is also a framework stub that throws by default in this environment, so it's
        // statically mocked to a no-op for the duration of this call to observe that cleanup
        // path without it being masked by an unrelated "Log not mocked" exception.
        mockStatic(Log::class.java).use { logMock ->
            logMock.`when`<Int> { Log.e(any(), any(), any()) }.thenReturn(0)

            val result = FontManager.loadCustomTypeface(context)

            assertNull(result)
        }
        assertFalse(customFontFile().exists())
    }

    @Test
    fun `clearCustomFont deletes the stored font file`() {
        val context = buildContext(mock(ContentResolver::class.java))
        customFontFile().writeBytes(byteArrayOf(1))
        assertTrue(customFontFile().exists())

        FontManager.clearCustomFont(context)

        assertFalse(customFontFile().exists())
    }

    @Test
    fun `clearCustomFont is a no-op when no font file exists`() {
        val context = buildContext(mock(ContentResolver::class.java))

        FontManager.clearCustomFont(context)

        assertFalse(customFontFile().exists())
    }

    // pickFontIntent() isn't covered here: android.content.Intent's setters (setType,
    // putExtra, addCategory) throw Android's "not mocked" stub exception in this plain JUnit
    // environment just like Typeface does, so its correctness (action, type, extras) is left
    // to the manual on-device verification called out in loadCustomTypeface's tests above.
}
