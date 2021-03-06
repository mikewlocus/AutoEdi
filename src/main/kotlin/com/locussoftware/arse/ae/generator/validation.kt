package com.locussoftware.arse.ae.generator

import kotlin.math.min

/**
 * Validates the code to ensure any square brackets would not cause errors in the output.
 *
 * @param arsecode The code being validated.
 * @return True if valid, false if not.
 */
fun validateSquareBrackets(arsecode: String) : Boolean {
    // Return true if string is empty
    if(arsecode.isEmpty()) {
        return true
    }

    // Check the number of open and closing brackets match
    if(arsecode.split("[").size != arsecode.split("]").size) {
        return false
    }

    // Check for segment creation conditional
    val sizeOfSplitWithOneOccurrence = 2
    if(arsecode.first() == '['
        && arsecode.last() == ']'
        && arsecode.split("[").size == sizeOfSplitWithOneOccurrence) {
        return true
    }

    // Check all conditions have a result
    arsecode.split("]").forEach {
        // Ensure the space between the last ']' and the next '[' (if it exists) contains no whitespace
        if(it.isEmpty() || (it.first() != '[' && it.trim().split("[")[0].isBlank())) {
            return false
        }
    }

    // Check for no further conditions after else
    val splitOnElse = arsecode.split("[]")
    if(splitOnElse.size > 1
        && (splitOnElse[1].contains("[")
                || splitOnElse[1].contains("]"))) {
        return false
    }

    return true
}

/**
 * Validates the code to ensure any round brackets would not cause errors in the output.
 *
 * @param arsecode The code being validated.
 * @return True if valid, false if not.
 */
fun validateRoundBrackets(arsecode: String) : Boolean {
    return bracketCount(arsecode.toCharArray().toList()) == 0
}

/**
 * Walk along the input, counting up per open bracket and counting down per closed bracket.
 *
 * @param input The input string, split up into characters.
 * @return The count of brackets.
 */
tailrec fun bracketCount(input: List<Char>, count: Int = 0) : Int {
    // Base case
    if(input.isEmpty()) {
        return count
    }

    // Calculate new count based on bracket presence
    val newCount = if(input[0] == '(') {
        count + 1
    } else if(input[0] == ')') {
        count - 1
    } else {
        count
    }

    // Return immediately if negative
    if(newCount < 0) {
        return newCount
    }

    // Continue recursion
    return bracketCount(input.subList(1, input.size), newCount)
}

/**
 * Validates the comparators of conditionals, ensuring erroneous comparators don't appear in the Java code.
 *
 * @param input The code being validated.
 * @return True if valid, false if not.
 */
fun validateComparators(input: String) : Boolean {
    return walkCondition(input.toCharArray().toList())
}

/**
 * Walks along the condition, character by character, validating comparators based on nearby characters.
 *
 * @param input The input string, split into characters.
 * @return True if no issues found, otherwise false.
 */
tailrec fun walkCondition(input: List<Char>, withinCondition: Boolean = false) : Boolean {
    if(input.isEmpty()) {
        return true
    }

    return when(input.first()) {
        '[' -> walkCondition(input.subList(1, input.size), true)
        ']' -> walkCondition(input.subList(1, input.size), false)
        '=' -> if(withinCondition && input.size > 1 && input[1] != '=')
            false
        else
            walkCondition(input.subList(min(input.size, 2), input.size), withinCondition)
        '!', '>', '<' -> if(withinCondition && input.size > 2 && input[2] == '=')
            false
        else
            walkCondition(input.subList(min(input.size, 3), input.size), withinCondition)
        else -> walkCondition(input.subList(1, input.size), withinCondition)
    }
}