package dev.bayhan.ttd.droid.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DateResolverTest {

    @Test
    fun `resolveToday replaces placeholder`() {
        val result = DateResolver.resolve("due:{{today}}", LocalDate.of(2025, 6, 16))
        assertEquals("due:2025-06-16", result)
    }

    @Test
    fun `resolvePlusDays`() {
        val result = DateResolver.resolve("due:{{+3d}}", LocalDate.of(2025, 6, 16))
        assertEquals("due:2025-06-19", result)
    }

    @Test
    fun `resolveMinusDays`() {
        val result = DateResolver.resolve("due:{{-1d}}", LocalDate.of(2025, 6, 16))
        assertEquals("due:2025-06-15", result)
    }

    @Test
    fun `resolvePlusWeeks`() {
        val result = DateResolver.resolve("due:{{+2w}}", LocalDate.of(2025, 6, 16))
        assertEquals("due:2025-06-30", result)
    }

    @Test
    fun `resolveMinusWeeks`() {
        val result = DateResolver.resolve("scheduled:{{-1w}}", LocalDate.of(2025, 6, 16))
        assertEquals("scheduled:2025-06-09", result)
    }

    @Test
    fun `resolvePlusMonths`() {
        val result = DateResolver.resolve("due:{{+1m}}", LocalDate.of(2025, 6, 16))
        assertEquals("due:2025-07-16", result)
    }

    @Test
    fun `resolveMinusMonths`() {
        val result = DateResolver.resolve("due:{{-3m}}", LocalDate.of(2025, 6, 16))
        assertEquals("due:2025-03-16", result)
    }

    @Test
    fun `resolvePlusYears`() {
        val result = DateResolver.resolve("due:{{+1y}}", LocalDate.of(2025, 6, 16))
        assertEquals("due:2026-06-16", result)
    }

    @Test
    fun `resolveMinusYears`() {
        val result = DateResolver.resolve("due:{{-1y}}", LocalDate.of(2025, 6, 16))
        assertEquals("due:2024-06-16", result)
    }

    @Test
    fun `resolveMultiplePlaceholders`() {
        val result = DateResolver.resolve("x {{today}} {{-7d}} done", LocalDate.of(2025, 6, 16))
        assertEquals("x 2025-06-16 2025-06-09 done", result)
    }

    @Test
    fun `resolveNoPlaceholders`() {
        val result = DateResolver.resolve("(A) Call mom @phone +Personal", LocalDate.of(2025, 6, 16))
        assertEquals("(A) Call mom @phone +Personal", result)
    }

    @Test
    fun `resolveWithLeadingPlus`() {
        val result = DateResolver.resolve("due:{{+10d}}", LocalDate.of(2025, 6, 16))
        assertEquals("due:2025-06-26", result)
    }

    @Test
    fun `resolveLeapYear`() {
        val result = DateResolver.resolve("due:{{+1d}}", LocalDate.of(2024, 2, 28))
        assertEquals("due:2024-02-29", result)
    }
}
