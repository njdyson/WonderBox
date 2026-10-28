package com.njdyson.wonderbox.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.njdyson.wonderbox.data.Child
import com.njdyson.wonderbox.data.Library
import com.njdyson.wonderbox.data.TREAT
import com.njdyson.wonderbox.data.Video
import com.njdyson.wonderbox.data.json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TreatsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val treats = listOf(Video("t1", "a", "Bluey", children = listOf("c1"), tier = TREAT), Video("t2", "b", "Gruffalo", children = listOf("c1"), tier = TREAT))

    private fun app(): AppState {
        context.getSharedPreferences("wonderbox", Context.MODE_PRIVATE).edit().clear()
            .putString("device_key", "k")
            .putString("library", json.encodeToString(Library.serializer(), Library(children = listOf(Child("c1", "Summer")), videos = treats)))
            .commit()
        return AppState(context)
    }

    @Test fun savingUsesUpAWonTreat() {
        val app = app()
        assertFalse(app.saveTreat("c1", treats[0]))
        app.earnTreat("c1")
        assertTrue(app.saveTreat("c1", treats[0]))
        assertEquals(0, app.treatsFor("c1"))
        assertEquals(listOf("Bluey"), app.savedTreatsFor("c1").map { it.title })
    }

    @Test fun theSameTreatCanBeSavedTwiceAndWatchingUsesUpOne() {
        val app = app()
        repeat(3) { app.earnTreat("c1") }
        app.saveTreat("c1", treats[0])
        app.saveTreat("c1", treats[1])
        app.saveTreat("c1", treats[0])
        val saved = app.savedTreatsFor("c1")
        assertEquals(3, saved.map { it.id }.distinct().size)
        app.useSavedTreat("c1", saved[0])
        assertEquals(listOf("Gruffalo", "Bluey"), app.savedTreatsFor("c1").map { it.title })
        // Kept on the TV between launches.
        assertEquals(listOf("Gruffalo", "Bluey"), AppState(context).savedTreatsFor("c1").map { it.title })
    }

    @Test fun theTreatsSectionOnlyOffersPicksWhileThereIsOneToPick() {
        val app = app()
        assertTrue(app.treatsSectionFor("c1").isEmpty())
        app.earnTreat("c1")
        assertEquals(listOf("Pick one to save"), app.treatsSectionFor("c1").map { it.title })
        app.saveTreat("c1", treats[1])
        assertEquals(listOf("My treats"), app.treatsSectionFor("c1").map { it.title })
    }
}
