package com.locussoftware.arse.ae.generator

/**
 * Validates the code to ensure any square brackets would not cause errors in the output.
 *
 * @param arsecode The code being validated.
 * @return True if valid, false if not.
 */
fun validateSquareBrackets(arsecode: String) : Boolean {
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