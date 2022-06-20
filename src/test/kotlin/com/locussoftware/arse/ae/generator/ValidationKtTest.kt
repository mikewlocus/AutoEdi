package com.locussoftware.arse.ae.generator

import org.junit.jupiter.api.Test

internal class ValidationKtTest {

    @Test
    fun testValidateSquareBrackets_ValidInput_ShouldReturnTrue() {
        val validInput = "[\$var == \"\"] \"x\" [] \"y\""
        kotlin.test.assertTrue(validateSquareBrackets(validInput))
    }

    @Test
    fun testValidateSquareBrackets_ValidInputNoSpaces_ShouldReturnTrue() {
        val validInputNoSpaces = "[\$var == \"\"]\"x\"[]\"y\""
        kotlin.test.assertTrue(validateSquareBrackets(validInputNoSpaces))
    }

    @Test
    fun testValidateSquareBrackets_ValidInputComplex_ShouldReturnTrue() {
        val validInputComplex = "[\$var == \"\"] \"x\" [\$var != \"\" && (4 == 5) || (3 + 2 == 5)] \"z\" [] \"y\""
        kotlin.test.assertTrue(validateSquareBrackets(validInputComplex))
    }

    @Test
    fun testValidateSquareBrackets_ValidInputNoBrackets_ShouldReturnTrue() {
        val validNoBrackets = "\"Regular input\""
        kotlin.test.assertTrue(validateSquareBrackets(validNoBrackets))
    }

    @Test
    fun testValidateSquareBrackets_ValidInputEmpty_ShouldReturnTrue() {
        val input = ""
        kotlin.test.assertTrue(validateSquareBrackets(input))
    }

    @Test
    fun testValidateSquareBrackets_ValidInputSegmentConditional_ShouldReturnTrue() {
        val input = "[x == y]"
        kotlin.test.assertTrue(validateSquareBrackets(input))
    }

    @Test
    fun testValidateSquareBrackets_InvalidInput_ShouldReturnFalse() {
        val invalidInput = "[\$var == \"\" \"x\" [] \"y\""
        kotlin.test.assertFalse(validateSquareBrackets(invalidInput))
    }

    @Test
    fun testValidateSquareBrackets_InvalidInputNoResult_ShouldReturnFalse() {
        val invalidInputNoResult = "[\$var == \"\"] \"x\" []"
        kotlin.test.assertFalse(validateSquareBrackets(invalidInputNoResult))
    }

    @Test
    fun testValidateSquareBrackets_InvalidInputNoFirstBracket_ShouldReturnFalse() {
        val invalidInputNoFirstBracket = "\$var == \"\"] \"x\" [] \"y\""
        kotlin.test.assertFalse(validateSquareBrackets(invalidInputNoFirstBracket))
    }

    @Test
    fun testValidateSquareBrackets_InvalidInputTooManyBrackets_ShouldReturnFalse() {
        val invalidInputTooManyBrackets = "[\$var == \"\"]] \"x\" [] \"y\""
        kotlin.test.assertFalse(validateSquareBrackets(invalidInputTooManyBrackets))
    }

    @Test
    fun testValidateSquareBrackets_InvalidInputElseCaseWrongPlace_ShouldReturnFalse() {
        val invalidInputElseCaseWrongPlace = "[\$var == \"\"] \"x\" [] \"y\" [4 == 5] \"z\""
        kotlin.test.assertFalse(validateSquareBrackets(invalidInputElseCaseWrongPlace))
    }

    @Test
    fun testValidateRoundBrackets_ValidInput_ShouldReturnTrue() {
        val validInput = "[(x == y || 3 == 3) && (y == z)]"
        kotlin.test.assertTrue(validateRoundBrackets(validInput))
    }

    @Test
    fun testValidateRoundBrackets_ValidInputNestedBrackets_ShouldReturnTrue() {
        val validInputNestedBrackets = "[(x == y || ((3 == 3) && (4 + 7 == 12))) && (y == z)]"
        kotlin.test.assertTrue(validateRoundBrackets(validInputNestedBrackets))
    }

    @Test
    fun testValidateRoundBrackets_ValidInputNoBrackets_ShouldReturnTrue() {
        val input = "[x == y]"
        kotlin.test.assertTrue(validateRoundBrackets(input))
    }

    @Test
    fun testValidateRoundBrackets_ValidInputEmpty_ShouldReturnTrue() {
        val input = "[x == y]"
        kotlin.test.assertTrue(validateRoundBrackets(input))
    }

    @Test
    fun testValidateRoundBrackets_InvalidInput_ShouldReturnFalse() {
        val invalidInput = "[(x == y || 3 == 3) && (y == z]"
        kotlin.test.assertFalse(validateRoundBrackets(invalidInput))
    }

    @Test
    fun testValidateRoundBrackets_InvalidInputNestedBrackets_ShouldReturnFalse() {
        val validInputNestedBrackets = "[(x == y || ((3 == 3) && (4 + 7 == 12)) && (y == z)]"
        kotlin.test.assertFalse(validateRoundBrackets(validInputNestedBrackets))
    }

    @Test
    fun testValidateRoundBrackets_InvalidInputBracketsWrongWay_ShouldReturnFalse() {
        val invalidInputBracketsWrongWay = "[)x == y || 3 == 3( && )y == z("
        kotlin.test.assertFalse(validateRoundBrackets(invalidInputBracketsWrongWay))
    }

    @Test
    fun testValidateRoundBrackets_InvalidInputAllClosingBrackets_ShouldReturnFalse() {
        val invalidInputAllClosingBrackets = "[)x == y || 3 == 3) && )y == z]"
        kotlin.test.assertFalse(validateRoundBrackets(invalidInputAllClosingBrackets))
    }

}