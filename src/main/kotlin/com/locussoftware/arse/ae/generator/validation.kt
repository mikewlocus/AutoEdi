package com.locussoftware.arse.ae.generator

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