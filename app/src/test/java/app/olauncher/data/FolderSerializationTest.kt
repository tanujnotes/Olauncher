package app.olauncher.data

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderSerializationTest {

    private fun folderApp(
        packageName: String = "com.example.app",
        user: String = "UserHandle{0}",
        activityClassName: String? = "com.example.app.MainActivity",
        isShortcut: Boolean = false,
        shortcutId: String = "",
    ) = FolderApp(packageName, user, activityClassName, isShortcut, shortcutId)

    @Test
    fun `folder app round trips through json`() {
        val app = folderApp(isShortcut = true, shortcutId = "s1")
        assertEquals(app, app.toJson().toFolderApp())
    }

    @Test
    fun `blank activity stays null`() {
        assertEquals(null, folderApp(activityClassName = "").toJson().toFolderApp().activityClassName)
    }

    @Test
    fun `folder round trips through json`() {
        val folder = FolderItem(
            id = "folder-1",
            name = "Work",
            apps = listOf(folderApp(), folderApp(packageName = "com.example.games")),
        )
        assertEquals(folder, folder.toJson().toFolderItem())
    }

    @Test
    fun `empty folder round trips through json`() {
        val folder = FolderItem("folder-2", "Games")
        assertEquals(folder, folder.toJson().toFolderItem())
    }

    @Test
    fun `folder array round trips through json array`() {
        val folders = listOf(
            FolderItem("folder-1", "Work", listOf(folderApp())),
            FolderItem("folder-2", "Games"),
        )
        val json = JSONArray(folders.map { it.toJson() }).toString()
        val parsed = JSONArray(json)
        val result = (0 until parsed.length()).map { parsed.getJSONObject(it).toFolderItem() }
        assertEquals(folders, result)
    }

    @Test
    fun `member equality matches same app`() {
        assertTrue(folderApp().memberEquals(folderApp()))
    }

    @Test
    fun `member equality distinguishes activity`() {
        assertFalse(folderApp().memberEquals(folderApp(activityClassName = "com.example.app.SecondActivity")))
    }

    @Test
    fun `app and shortcut are never equal`() {
        assertFalse(folderApp().memberEquals(folderApp(isShortcut = true, shortcutId = "s1")))
    }

    @Test
    fun `shortcuts with same id and package are equal`() {
        assertTrue(
            folderApp(isShortcut = true, shortcutId = "s1")
                .memberEquals(folderApp(isShortcut = true, shortcutId = "s1"))
        )
    }

    @Test
    fun `shortcuts with different package are not equal`() {
        assertFalse(
            folderApp(packageName = "com.example.app", isShortcut = true, shortcutId = "s1")
                .memberEquals(folderApp(packageName = "com.example.other", isShortcut = true, shortcutId = "s1"))
        )
    }
}