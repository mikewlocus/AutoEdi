import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import java.io.BufferedReader
import java.io.File

internal class FieldHandlersKtTest {

    // Import fields
    private val bufferedCustomFieldReader: BufferedReader = File("variables.csv").bufferedReader()
    private val customFieldCsv = bufferedCustomFieldReader.use { it.readText() }

    private val fields = customFieldCsv.split(System.lineSeparator())

    private val mockSchema = listOf(
            "SG1,-,-,-,-,+,SEGMENT GROUP 1 - START,,,,,,9,BookLineItem.Booking\n",
            ",,,,,|,RFF,,,,REFERENCE,,1,\n",
            ",,,,,|,,C506,,,REFERENCE,,,\n",
            ",,,,,|,,,1153,,Reference code qualifier ,\"\"\"BN\"\"\",,\n",
            ",,,,,|,,,1154,,Reference identifier,\$bi_bookingNumber,,\n",
            ",,,,,|,,,1156,,Document line identifier,---,,\n",
            ",,,,,|,,,1056,,Version identifier,---,,\n",
            ",,,,,|,,,1060,,Revision identifier,---,,\n",
            ",,,,,|,DTM,,,,DATE/TIME/PERIOD,DO NOT CREATE,9,\n",
            ",,,,,|,,C507,,,DATE/TIME/PERIOD,,,\n",
            ",,,,,|,,,2005,,Date or time or period function code qualifier,---,,\n",
            ",,,,,|,,,2380,,Date or time or period value,---,,\n",
            ",,,,,|,,,2379,,Date or time or period format code ,---,,\n",
            "------,-,-,-,-,-,SEGMENT GROUP 1 - END,,,,,,,")

    private val mockSchemaWithInnerSegment = listOf(
        "SG1,-,-,-,-,+,SEGMENT GROUP 1 - START,,,,,,9,BookLineItem.Booking\n",
        ",,,,,|,RFF,,,,REFERENCE,,1,\n",
        ",,,,,|,,C506,,,REFERENCE,,,\n",
        ",,,,,|,,,1153,,Reference code qualifier ,\"\"\"BN\"\"\",,\n",
        ",,,,,|,,,1154,,Reference identifier,\$bi_bookingNumber,,\n",
        ",,,,,|,,,1156,,Document line identifier,---,,\n",
        ",,,,,|,,,1056,,Version identifier,---,,\n",
        ",,,,,|,,,1060,,Revision identifier,---,,\n",
        ",,,,,|,DTM,,,,DATE/TIME/PERIOD,DO NOT CREATE,9,\n",
        ",,,,,|,,C507,,,DATE/TIME/PERIOD,,,\n",
        ",,,,,|,,,2005,,Date or time or period function code qualifier,---,,\n",
        ",,,,,|,,,2380,,Date or time or period value,---,,\n",
        ",,,,,|,,,2379,,Date or time or period format code ,---,,\n",
        "SG2,-,-,-,+,|,SEGMENT GROUP 2 - START,,,,,,9,Booking>ShippingInstruction\r",
        ",,,,|,|,RFF,,,,REFERENCE,,1,\n",
        ",,,,|,|,,C506,,,REFERENCE,,,\n",
        ",,,,|,|,,,1153,,Reference code qualifier ,\"\"\"BN\"\"\",,\n",
        ",,,,|,|,,,1154,,Reference identifier,\$si_mrnDateIssue,,\n",
        ",,,,|,|,,,1156,,Document line identifier,\$crm_dgsEmergencyEmail,,\n",
        ",,,,|,|,,,1056,,Version identifier,---,,\n",
        "------,-,-,-,-,|,SEGMENT GROUP 2 - END,,,,,,,",
        "------,-,-,-,-,-,SEGMENT GROUP 1 - END,,,,,,,")

    /**
     * Test that a pure field input, along with any parameters, are processed as expected.
     */
    @Test
    fun getFields() {
        // Test for blank
        kotlin.test.assertEquals("", getFields("", this.fields))

        // Test for unknown field
        val unknownField = "\$unknownField"

        kotlin.test.assertEquals(unknownField, getFields(unknownField, this.fields))

        // Test for basic field
        val basicField = ""
    }

    /**
     * Test that parameters are split properly, ignoring any nested parameters.
     */
    @Test
    fun splitParameters() {
        // Test blank input
        kotlin.test.assertEquals(listOf(""), splitParameters(listOf()))

        // Test single value
        val singleValueInput = "\$test"

        kotlin.test.assertEquals(listOf(singleValueInput), splitParameters(singleValueInput.toCharArray().toList()))

        // Test multiple values
        val multipleValueInput = "\$test;\$test2{\"Test!\"}".toCharArray().toList()
        val multipleValueExpected = listOf("\$test", "\$test2{\"Test!\"}")

        kotlin.test.assertEquals(multipleValueExpected, splitParameters(multipleValueInput))

        // Test complex values
        val complexValueInput = "\$test;\$test2{\$test3{'x'; 'y'}; test4{'x'}}; test5{\$test6}".toCharArray().toList()
        val complexValueExpected = listOf("\$test", "\$test2{\$test3{'x'; 'y'}; test4{'x'}}", " test5{\$test6}")

        kotlin.test.assertEquals(multipleValueExpected, splitParameters(multipleValueInput))
    }

    /**
     * Test that the condition ArseCode is being processed correctly into a Java conditional.
     */
    @Test
    fun createConditional() {
        // Test blank input
        kotlin.test.assertEquals("", createConditional(listOf(), "", fields))

        // Test simple if/else conditional, with fields
        val fieldSetter = "reference.setReferenceCodeQualifier("
        val simpleConditionalInput = "[\$si_mrn == \"\"] \"x\" [] \"y\"".split("[", "]").drop(1)
        val simpleConditionalExpected = "\tif ((shippingInstruction.getMrn() != null) && (shippingInstruction." +
                "getMrn()==\"\")) {\n\t\treference.setReferenceCodeQualifier( \"x\" );\n\t} else {\n\t\treference" +
                ".setReferenceCodeQualifier( \"y\");\n \t}"

        kotlin.test.assertEquals(simpleConditionalExpected,
            createConditional(simpleConditionalInput, fieldSetter, fields))

        // Test a complex if/else if/else conditional, with fields and parameters
        val complexConditionalInput = "[\$si_mrn == \"\"] \"x\" [\$strlen{\$si_mrn} < 4] \"y\" [] \"z\""
            .split("[", "]")
            .drop(1)
        val complexConditionalExpected = "\tif ((shippingInstruction.getMrn() != null) && (shippingInstruction." +
                "getMrn()==\"\")) {\n\t\treference.setReferenceCodeQualifier( \"x\" );\n\t} " +
                "else if((shippingInstruction.getMrn() != null) && (shippingInstruction.getMrn().length()<4)) " +
                "{\n\t\treference.setReferenceCodeQualifier( \"y\" );\n\t} else " +
                "{\n\t\treference.setReferenceCodeQualifier( \"z\");\n \t}"

        kotlin.test.assertEquals(complexConditionalExpected,
            createConditional(complexConditionalInput, fieldSetter, fields))
    }

    /**
     * Test Java method parameters are being successfully generated from the schema.
     */
    @Test
    fun createParameters() {
        // Test blank input
        kotlin.test.assertEquals(listOf(), createParameters(listOf(), fields))

        // Test parameter creation with mocked schema
        val expected = listOf("@Nonnull Booking booking")
        kotlin.test.assertEquals(expected, createParameters(mockSchema, fields))

        // Test parameter creation with mocked schema and inner segment, to check looped parameters are being ignored
        val expectedWithInner = listOf("@Nonnull Booking booking",
            "@Nonnull CommodityBookLineItemList commodityBookLineItemList")
        kotlin.test.assertEquals(expectedWithInner, createParameters(mockSchemaWithInnerSegment, fields))
    }

    @Test
    fun getListOfParams() {
    }

    @Test
    fun createLocals() {
    }

}