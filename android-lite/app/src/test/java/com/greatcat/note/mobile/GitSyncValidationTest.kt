package com.greatcat.note.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GitSyncValidationTest {
    @Test
    fun acceptsSafeHttpsRemoteAndBranch() {
        assertEquals("https://github.com/example/notes.git", validateRemoteUrl(" https://github.com/example/notes.git "))
        assertEquals("main", validateBranch(" main "))
    }

    @Test
    fun rejectsEmbeddedCredentialsAndUnsafeBranches() {
        assertThrows(IllegalArgumentException::class.java) {
            validateRemoteUrl("https://token@github.com/example/notes.git")
        }
        assertThrows(IllegalArgumentException::class.java) { validateBranch("../main") }
    }
}
