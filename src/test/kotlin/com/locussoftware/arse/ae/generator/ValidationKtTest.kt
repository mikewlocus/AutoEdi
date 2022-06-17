package com.locussoftware.arse.ae.generator

import org.junit.jupiter.api.Test

internal class ValidationKtTest {

    @Test
    fun testValidateSquareBrackets_ValidInput_ShouldReturnTrue() {
        val validInput = "[\$var == \"\"] \"x\" [] \"y\""
        kotlin.test.assertTrue(validateSquareBrackets(validInput))
    }

    @Test
    fun testShouldValidateSquareBrackets_ValidInputNoSpaces_ShouldReturnTrue() {
        val validInputNoSpaces = "[\$var == \"\"]\"x\"[]\"y\""
        kotlin.test.assertTrue(validateSquareBrackets(validInputNoSpaces))
    }

    @Test
    fun testShouldValidateSquareBrackets_ValidInputComplex_ShouldReturnTrue() {
        val validInputComplex = "[\$var == \"\"] \"x\" [\$var != \"\" && (4 == 5) || (3 + 2 == 5)] \"z\" [] \"y\""
        kotlin.test.assertTrue(validateSquareBrackets(validInputComplex))
    }

    @Test
    fun testShouldValidateSquareBrackets_ValidInputNoBrackets_ShouldReturnTrue() {
        val validNoBrackets = "\"Regular input\""
        kotlin.test.assertTrue(validateSquareBrackets(validNoBrackets))
    }

    @Test
    fun testShouldValidateSquareBrackets_InvalidInput_ShouldReturnFalse() {
        val invalidInput = "[\$var == \"\" \"x\" [] \"y\""
        kotlin.test.assertFalse(validateSquareBrackets(invalidInput))
    }

    @Test
    fun testShouldValidateSquareBrackets_InvalidInputNoResult_ShouldReturnFalse() {
        val invalidInputNoResult = "[\$var == \"\"] \"x\" []"
        kotlin.test.assertFalse(validateSquareBrackets(invalidInputNoResult))
    }

    @Test
    fun testShouldValidateSquareBrackets_InvalidInputNoFirstBracket_ShouldReturnFalse() {
        val invalidInputNoFirstBracket = "\$var == \"\"] \"x\" [] \"y\""
        kotlin.test.assertFalse(validateSquareBrackets(invalidInputNoFirstBracket))
    }

    @Test
    fun testShouldValidateSquareBrackets_InvalidInputTooManyBrackets_ShouldReturnFalse() {
        val invalidInputTooManyBrackets = "[\$var == \"\"]] \"x\" [] \"y\""
        kotlin.test.assertFalse(validateSquareBrackets(invalidInputTooManyBrackets))
    }

    @Test
    fun testShouldValidateSquareBrackets_InvalidInputElseCaseWrongPlace_ShouldReturnFalse() {
        val invalidInputElseCaseWrongPlace = "[\$var == \"\"] \"x\" [] \"y\" [4 == 5] \"z\""
        kotlin.test.assertFalse(validateSquareBrackets(invalidInputElseCaseWrongPlace))
    }

}