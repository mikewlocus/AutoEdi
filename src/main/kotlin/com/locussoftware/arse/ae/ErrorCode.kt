package com.locussoftware.arse.ae

/**
 * Contains the error codes which can be encountered during the generation of a specification.
 *
 * @author Mike Wayne
 */
enum class ErrorCode(val code: Int, val message: String) {
    UNKNOWN_ERROR(1000, "I don't even know how you've messed this up so badly."),
    SQUARE_BRACKET_ERROR(1001, "Incorrect square brackets for conditional."),
    ROUND_BRACKET_ERROR(1002, "Incorrect round brackets."),
    VARIABLE_NOT_FOUND_ERROR(1003, "Variable not found."),
    VARIABLE_NOT_IMPLEMENTED_ERROR(1004, "Variable not implemented."),
    INSUFFICIENT_LOOPING_LOGIC(1005, "Variable does not have sufficient looping logic."),
    INCORRECT_NUMBER_OF_PARAMETERS(1006, "Incorrect number of parameters for variable."),
    INCORRECT_NUMBER_OF_SPEECH_MARKS(1007, "Incorrect number of speech marks."),
    INCORRECT_COMPARATOR_ERROR(1008, "Incorrect comparator.");

    companion object {
        /**
         * Loops through the error messages to find the given error code, and returns its error message if found.
         *
         * @param code The error code for which to get a message.
         * @return The error message of the given error code, or a default one if not found.
         */
        fun getMessageByCode(code: Int) : String {
            values().forEach {
                if(it.code == code) {
                    return it.message
                }
            }

            return "Error code not found. This could be bad. Or not, who knows?"
        }
    }
}