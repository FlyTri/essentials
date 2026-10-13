package com.sameerasw.essentials.island

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sameerasw.essentials.island.ui.condensedTitleWidth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CondensedLineTest {
    private val lineWidth = 360.dp
    private val cameraSlot = 40.dp
    private val cellSize = 28.dp
    private val edgePad = 12.dp

    private fun title(natural: Dp, endInset: Dp = 0.dp) =
        condensedTitleWidth(natural, lineWidth, cameraSlot, cellSize, edgePad, endInset)

    // Mirrors CondensedLine: each half is the title width plus the edge padding, with the camera slot between them.
    private fun side(natural: Dp, endInset: Dp = 0.dp) = title(natural, endInset) + edgePad

    private fun pillWidth(natural: Dp, endInset: Dp = 0.dp) = side(natural, endInset) * 2 + cameraSlot

    @Test fun shortTitleHugsTheText() {
        assertEquals(60.dp, title(natural = 60.dp))
        assertEquals(184.dp, pillWidth(natural = 60.dp))
    }

    @Test fun longTitleStopsAtTheRoomBesideTheCamera() = assertEquals(148.dp, title(natural = 900.dp))

    @Test fun titleNeverNarrowerThanTheIconBlock() = assertEquals(cellSize, title(natural = 4.dp))

    @Test fun cameraSlotStaysAtThePillCentre() {
        listOf(0.dp, 60.dp, 140.dp, 900.dp).forEach { natural ->
            val slotStart = side(natural)
            assertEquals("natural=$natural", pillWidth(natural) - slotStart - cameraSlot, slotStart)
        }
    }

    @Test fun longestTitleStillFitsTheLineWidth() = assertTrue(pillWidth(natural = 900.dp) <= lineWidth)

    @Test fun rightEdgeStaysInsideHalfTheMaxWidth() {
        listOf(0.dp, 60.dp, 900.dp).forEach { natural ->
            listOf(0.dp, 46.dp).forEach { endInset ->
                val fromCentre = cameraSlot / 2 + edgePad + title(natural, endInset)
                val budget = (lineWidth - endInset) / 2
                assertTrue("natural=$natural endInset=$endInset gave $fromCentre", fromCentre <= budget)
            }
        }
    }

    @Test fun titleGrowsMonotonicallyWithItsText() {
        val widths = listOf(0.dp, 30.dp, 60.dp, 140.dp, 900.dp).map { title(it) }
        assertEquals(widths.sortedBy { it.value }, widths)
    }
}
