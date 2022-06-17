import com.locussoftware.arse.ae.ErrorCode

/* This file contains a collection of methods for acting directly on the input of the input column. */

/**
 * Processes a field variable, returning its code string, and handling any of its parameters recursively.
 *
 * @param inputFields The input field to be processed. Can include parameters.
 * @param allFields The fields list imported from the variables table.
 * @return The code string of the input fields.
 */
fun getFields(inputFields: String, allFields: List<String>) : String {

    // Split field from its parameter (limited to 2; the field, followed by all parameters)
    val fieldsIn = inputFields.split("[{]".toRegex(), 2)
    // Gets the code for the field
    val fieldCode = getFieldOrNull(fieldsIn[0].replace("\r", "").trim('}', ' '), allFields)

    // Lone field; base case
    if(fieldsIn.size == 1) {
        // Return the field's code, or the variable if the code is null (not found)
        return fieldCode ?: fieldsIn[0]
    }

    // Field with parameters; continue recursion
    // Split the parameters found in the field
    val parameters = splitParameters(fieldsIn[1].toCharArray().toList())

    return if(fieldCode == null) {
        // No field found, return data as entered
        inputFields
    } else {
        // Return this field's code, joined with any code of its parameters

        val fieldSplitOnParams = fieldCode.split("{P}")

        // Check that the correct number of parameters have been provided
        if(fieldSplitOnParams.size == parameters.size + 1) {
            // Add field to code and process parameters through recursion
            parameters.joinToString("") {
                val paramCode = getFields(it.trim(' ', '}').replace("\r", ""), allFields)

                // Add parameter to code
                fieldSplitOnParams[parameters.indexOf(it)] + paramCode
            }.replace(';', ',') + fieldSplitOnParams.last()
        } else{
            errors[rowCount] = ErrorCode.INCORRECT_NUMBER_OF_PARAMETERS.code
            ""
        }
    }
}

/**
 * Splits a field input with parameters by the ';' delimiter, ignoring any delimiters nested within further parameters.
 *
 * @param input The parameter string, as a list of chars.
 * @return A list of parameters, with sub parameters intact (not split)
 */
tailrec fun splitParameters(input: List<Char>, depth: Int = 0, output: List<String> = listOf("")) : List<String> {
    if(input.isEmpty()) {
        return output
    }

    // Increase or decrease depth, allowing nested semicolons to be skipped over
    val newDepth = when(input[0]) {
        '{' -> depth + 1
        '}' -> depth - 1
        else -> depth
    }

    return if(input[0] == ';' && depth == 0) {
        // Add new split to list
        splitParameters(input.subList(1, input.size), depth, output + listOf(""))
    } else{
        val outputList = output.subList(0, output.size - 1) + listOf(output[output.size - 1] + input[0])

        // Carry on adding characters to this element of the list
        splitParameters(input.subList(1, input.size), newDepth, outputList)
    }
}

/**
 * Processes conditional ArseCode, generating and returning conditional Java code.
 *
 * @param conditCode A list containing the conditional ArseCode, split on square brackets. Even positions contain
 * conditionals, odd positions contain values.
 * @param fieldSetter The setter code for the field containing the conditional. E.g. 'c506.setReferenceCodeQualifier('.
 * @param fields The fields list imported from the variables table.
 * @return A string containing a fully processed conditional in Java.
 */
tailrec fun createConditional(conditCode: List<String>,
                              fieldSetter: String,
                              fields: List<String>,
                              conditional: String = "",
                              fieldCloser: String = "",
                              inline: Boolean = false): String {
    // Empty condition, base case; return code
    if(conditCode.isEmpty()) {
        return if (conditional.isEmpty())
            ""
        else
            conditional + if(!inline) "\t}" else ""
    }

    val regex = "&&|\\|\\|"

    // if((x != null && x == 'a') || ((y != null && y == 'b'))) /
    // if((x != null && y != null) && (x == 'a' || (y == 'b'))) X

    // [X, ==, 'a', ||, (, Y, ==, 'b', )]

    // Split the conditional on comparators and brackets, retaining the split values in the list
    val condition = createConditionWithChecks(conditCode[0].split("(?<=($regex))|(?=($regex))".toRegex()), fields)
    // Create any necessary local variables for the conditional's value
    val locals = createLocals(conditCode[1].split(";", "{", "}"), fields)
    // Create any null checks for the conditional's value
    val nullCheck = createMultiNullCheck(listOf(conditCode[1]), fields)

    // Build code output string
    // ...SchedulePort schedulePort = booking.getSchedulePort()...
    val localsString = if(locals != "") "$locals\t\t" else ""
    // ...if(schedulePort != null) {...
    val nullCheckString = if (nullCheck != "") "if(${nullCheck}) {\n\t\t\t" else ""
    // ...tdt.setTransportIdentifier(x);...
    val fieldString = fieldSetter + getFields(conditCode[1], fields) + fieldCloser + if(!inline) ");" else ""
    // ...}...
    val nullCheckCloser = if (nullCheck != "") "\n\t\t}" else ""

    val conditionalCode = localsString +
            nullCheckString +
            fieldString +
            nullCheckCloser +
            "\n"

    return if(conditional == "") {
        // if
        val ifCode = if(!inline) {
            "\tif ($condition) {\n\t\t$conditionalCode"
        } else {
            "($condition) ? $conditionalCode : " + if(conditCode.size <= 2) "\"\"" else ""
        }

        createConditional(conditCode.subList(2, conditCode.size),
            fieldSetter,
            fields,
            conditional + ifCode,
            fieldCloser,
            inline)
    } else{
        // Blank square brackets [] indicates an else condition
        if(conditCode[0].isBlank()) {
            // else
            val elseCode = if(!inline) {
                "\t} else {\n\t\t$conditionalCode"
            } else {
                conditionalCode
            }

            createConditional(conditCode.subList(2, conditCode.size),
                fieldSetter,
                fields,
                conditional + elseCode,
                fieldCloser,
                inline)
        } else{
            // else if
            val elseifCode = if(!inline) {
                "\t} else if($condition) {\n\t\t$conditionalCode"
            } else {
                "($condition) ? $conditionalCode : " + if(conditCode.size <= 2) "\"\"" else ""
            }

            createConditional(conditCode.subList(2, conditCode.size),
                fieldSetter,
                fields,
                conditional + elseifCode,
                fieldCloser,
                inline)
        }
    }
}

/**
 *
 */
tailrec fun createConditionWithChecks(splitCondition: List<String>,
                                      fields: List<String>,
                                      output: String = "") : String {
    if(splitCondition.isEmpty()) {
        return output
    }

    val regex = "==|!=|!|<|>|<=|>=|&&|\\|\\||\\(|\\)"

    // Splits the condition, and attempts to find the code for any defined fields
    val condition = splitCondition[0]
        .split("(?<=($regex))|(?=($regex))".toRegex())
        .joinToString("") {
            getFields(it, fields).trim(' ')
        }

    // Formats the condition along with its null check
    val newOutput = output + if(condition.length > 2) {
        // Create any null checks for the conditional itself
        val condNullCheck = createMultiNullCheck(splitCondition[0].split("($regex|;|,|\\[|]|\\{|})".toRegex()), fields)
        // Get any additional checks
        val additionalChecks = getAllAdditionalChecks(splitCondition[0].split("($regex|\\{|}|;|,|\\[|])".toRegex()), fields)

        "(" +
        (if (additionalChecks.trim() != "") "($additionalChecks) && " else "") +
        (if (condNullCheck != "") "($condNullCheck) && " else "") +
        condition +
        ")"
    } else {
        condition
    }

    return createConditionWithChecks(splitCondition.subList(1, splitCondition.size), fields, newOutput)
}

tailrec fun getAllAdditionalChecks(fieldsToCheck: List<String>, fields: List<String>, output: String = "") : String {
    if(fieldsToCheck.isEmpty()) {
        return output
    }

    val field = getFieldOrNull(fieldsToCheck[0].trim(), fields, true)
    val newOutput = output + if (field != null) field.split(",")[FIELD_ADDITIONAL_COLUMN] else ""

    return getAllAdditionalChecks(fieldsToCheck.subList(1, fieldsToCheck.size), fields, newOutput)
}

/**
 * Creates a list of Java method parameters for segment group and loose method definitions.
 *
 * @param sheetLines The input schema CSV split by line breaks.
 * @param fields The variables table CSV split by line breaks.
 * @return A list of Java method parameters.
 */
tailrec fun createParameters(sheetLines: List<String>,
                             fields: List<String>,
                             parameters: List<String> = listOf(),
                             loopedParams: List<List<String>> = listOf()) : List<String> {
    // Empty list, base case; return parameters
    if(sheetLines.isEmpty()) {
        return parameters
    }

    val regex = "(==|!=|!|<|>|<=|>=|&&|\\|\\||\\(|\\)|\\{|}|;|\\[|])".toRegex()

    // Split the current CSV line on commas to get cell values
    val currentLine = sheetLines[0].split(",")
    // Get the list of parameters from the value column, minus the ones in any looping logics to be ignored
    val params = getListOfParams(currentLine[VALUE_COLUMN].split(regex), fields).minus(loopedParams.flatten())

    // End of an inner segment, remove previous parameters to be ignored
    val paramsToIgnore = if(currentLine[SEGMENT_GROUP_COLUMN] == "------") {
        loopedParams.subList(0, loopedParams.size - 1)
    } else{
        loopedParams
    }

    // Add inner segment looped parameters to be ignored
    val newLoopedParams = if(sheetLines.size > 1) {
        // Get the next line
        val nextLine = sheetLines[1].split(",")

        // Check the next line, as the current line would get the opening parameters
        if(nextLine[0] != "" && nextLine[LOOP_COLUMN].split(">", ";", ".").size > 1) {
            nextLine[LOOP_COLUMN]
                .replace("\r", "")
                .replace("\n", "")
                .split(">", ";", ".")
        } else{
            listOf()
        }
    } else{
        listOf()
    }

    // Format the parameters properly
    val newParamsList = (parameters + params.map {
        if(it.split(":").size > 1)
            // ??? (possibly to do with looping)
            " get"
        else
            // @Nonnull SchedulePort schedulePort
            "@Nonnull $it " + it.first().toLowerCase() + it.substring(1, it.length)
    }).toSet().toList()

    return createParameters(sheetLines.subList(1, sheetLines.size),
        fields,
        newParamsList,
        paramsToIgnore + listOf(newLoopedParams))
}

/**
 * Gets the list of parameters required for a field.
 *
 * @param fields The list of input fields which need to be checked for parameters.
 * @param allFields The list of variables in the variables table CSV.
 * @return A list of parameters required for a field.
 */
fun getListOfParams(fields: List<String>, allFields: List<String>, output: List<String> = listOf()) : List<String> {
    if(fields.isEmpty()) {
        return output
    }

    // Get the whole field, if it exists
    val field = getFieldOrNull(fields[0], allFields, true)

    val parameters = if(field != null) {
        // Split the field into its columns
        val fieldParams = field.split(",")

        if(fieldParams[FIELD_PARAMETER_COLUMN] != "") {
            val regex = "(==|!=|!|<|>|<=|>=|&&|\\|\\||\\(|\\)|\\{|}|;|\\[|])".toRegex()

            // Set to a list of found parameters, along with the parameters required for any local variables
            fieldParams[FIELD_PARAMETER_COLUMN].split(";") +
                    getListOfParams(fieldParams[FIELD_LOCALS_COLUMN].split(regex), allFields)
        } else {
            // No parameters required
            listOf()
        }
    } else {
        // Field not found, set to empty list
        listOf()
    }

    // Get the parameters for the next field
    return getListOfParams(fields.subList(1, fields.size), allFields, output + parameters)
}

/**
 * Creates local variables for a list of fields, based on the value of the locals column in the variables table.
 *
 * @param inputFields The input fields for which locals need to be generated.
 * @param fields The list of variables from the fields table CSV.
 * @return Java code for the creation of local variables, required by fields in scope.
 */
tailrec fun createLocals(inputFields: List<String>, fields: List<String>, locals: String = "") : String {
    if(inputFields.isEmpty()) {
        return locals
    }

    // Get the local variables for this field, if they exist
    val fieldLocals = if(getLocalOrNull(inputFields[0], fields) != null)
        getLocalOrNull(inputFields[0], fields) + ";\n"
    else
        ""

    // Continue recursion through the rest of the input fields
    return createLocals(inputFields.subList(1, inputFields.size), fields, locals + fieldLocals)
}