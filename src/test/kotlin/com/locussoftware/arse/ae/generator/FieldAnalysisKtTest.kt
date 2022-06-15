import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import java.io.BufferedReader
import java.io.File
import java.lang.IndexOutOfBoundsException

internal class FieldAnalysisKtTest {
    // Import fields
    private val bufferedCustomFieldReader: BufferedReader = File("variables.csv").bufferedReader()
    private val customFieldCsv = bufferedCustomFieldReader.use { it.readText() }

    private val fields = customFieldCsv.split(System.lineSeparator())

    // Shared test values
    private val mockFields = listOf("Schedule Port,\$bi_loadPortUn,booking.getLoadPort().getUnLocode().getLocodeNoSpaces(),String,1;2,,,,,",
        "Count of current segment,\$count,count,int,N,,,\$sched,,\n",
        "Schedule Port,\$sched,bookingItinerary.getLoadPort(voyage),SchedulePort,N,,,,,")
    private val unknownVariable = "\$TEST"

    /**
     * Test that the method will return the correct value for a variable in the table, and null for one which isn't.
     */
    @Test
    fun getFieldOrNull() {
        // Test for null
        kotlin.test.assertEquals(null, getFieldOrNull(this.unknownVariable, this.fields))

        // Test for a field
        val knownVariable = "\$count"
        val expectedResultForKnownVariable = "count"

        kotlin.test.assertEquals(expectedResultForKnownVariable, getFieldOrNull(knownVariable, this.fields))
    }

    /**
     * Test that the method will either return a local variable definition for a variable in the table, or null.
     */
    @Test
    fun getLocalOrNull() {
        // Test for null
        kotlin.test.assertEquals(null, getLocalOrNull(this.unknownVariable, this.fields))

        // Test for a field
        val knownVariableWithLocal = "\$count"
        val expectedResultForKnownVariable = "SchedulePort schedulePort = () ? bookingItinerary.getLoadPort(voyage) : null"

        kotlin.test.assertEquals(expectedResultForKnownVariable, getLocalOrNull(knownVariableWithLocal, this.mockFields))
    }

    /**
     * Test that values are correctly returned from the null values column.
     */
    @Test
    fun fieldNullString() {
        // Test for null
        kotlin.test.assertEquals(null, fieldNullString(this.unknownVariable, this.fields))

        // Test for 'N'
        val knownCodeWithNullStringN = "this.count"

        kotlin.test.assertEquals(null, fieldNullString(knownCodeWithNullStringN, this.fields))

        // Test for '1;2'
        val knownCodeWithNullStringPositions = "booking.getLoadPort().getUnLocode().getLocodeNoSpaces()"
        val expectedResultForKnownCodeWithNullStringPositions = "1;2"

        kotlin.test.assertEquals(expectedResultForKnownCodeWithNullStringPositions,
            fieldNullString(knownCodeWithNullStringPositions, this.mockFields))
    }

    /**
     * Test that values are correctly returned from the additional checks column.
     */
    @Test
    fun fieldAdditionalChecks() {
        // Test for null
        kotlin.test.assertEquals(null, fieldAdditionalChecks(this.unknownVariable, this.fields))

        // Test for returned value
        val knownVariableWithNullCheckN = "bookingItinerary.getLoadTerminalBySchedulePort(bookingItinerary.getLoadPort(voyage))"
        val expectedResultForKnownVariableWithNullCheckPositions = "bookingItinerary.getLoadPort(voyage) != null"

        kotlin.test.assertEquals(expectedResultForKnownVariableWithNullCheckPositions,
            fieldAdditionalChecks(knownVariableWithNullCheckN, this.fields))
    }

    /**
     * Test that values are correctly returned from any column.
     */
    @Test
    fun getFieldValueAtColumnUsingCodeToSearch() {
        // Test for null
        kotlin.test.assertEquals(null,
            getFieldValueAtColumnUsingCodeToSearch(this.unknownVariable, this.fields, FIELD_CODE_COLUMN))

        // Test for out of bounds column, but with known variable
        val columnOutOfBounds = 200
        val knownCode = "bookingItinerary.getLoadPort(voyage)"

        kotlin.test.assertFailsWith<IndexOutOfBoundsException> {
            getFieldValueAtColumnUsingCodeToSearch(knownCode, this.mockFields, columnOutOfBounds)
        }

        // Test for returned value in a valid column
        val expectedResultForValidColumn = "Schedule Port"
        val testColumn = 0

        kotlin.test.assertEquals(expectedResultForValidColumn,
            getFieldValueAtColumnUsingCodeToSearch(knownCode, this.mockFields, testColumn))

        // Test for returned value in a second valid column
        val expectedResultForValidColumn2 = "SchedulePort"
        val testColumn2 = 3

        kotlin.test.assertEquals(expectedResultForValidColumn2,
            getFieldValueAtColumnUsingCodeToSearch(knownCode, this.mockFields, testColumn2))
    }

}