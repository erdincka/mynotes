package uk.kayalab.mynotes.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderTreeTest {
    private val work = Folder(id = 1, name = "Work", parentId = null)
    private val projects = Folder(id = 2, name = "Projects", parentId = 1)
    private val alpha = Folder(id = 3, name = "Alpha", parentId = 2)
    private val home = Folder(id = 4, name = "Home", parentId = null)
    private val folders = listOf(work, projects, alpha, home)

    @Test
    fun descendantsCoverEveryLevelBelowTheRoot() {
        assertEquals(setOf(2L, 3L), FolderTree.descendantIds(folders, 1))
        assertEquals(setOf(3L), FolderTree.descendantIds(folders, 2))
        assertTrue(FolderTree.descendantIds(folders, 4).isEmpty())
    }

    @Test
    fun subtreeIncludesTheRootItself() {
        assertEquals(setOf(1L, 2L, 3L), FolderTree.subtreeIds(folders, 1))
    }

    @Test
    fun aFolderCannotMoveIntoItselfOrItsOwnDescendants() {
        assertFalse(FolderTree.canMove(folders, 1, 1))
        assertFalse(FolderTree.canMove(folders, 1, 3))
        assertTrue(FolderTree.canMove(folders, 3, 4))
        assertTrue(FolderTree.canMove(folders, 1, null))
    }

    @Test
    fun pathLabelWalksFromRootToLeaf() {
        assertEquals("Work / Projects / Alpha", FolderTree.pathLabel(folders, 3))
        assertEquals("", FolderTree.pathLabel(folders, null))
        assertEquals("", FolderTree.pathLabel(folders, 99))
    }

    @Test
    fun corruptCyclesDoNotHangTheHelpers() {
        val a = Folder(id = 10, name = "A", parentId = 11)
        val b = Folder(id = 11, name = "B", parentId = 10)
        val cyclic = listOf(a, b)
        assertEquals(setOf(11L), FolderTree.descendantIds(cyclic, 10))
        assertEquals("B / A", FolderTree.pathLabel(cyclic, 10))
    }
}
