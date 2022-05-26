
/**
 *
 */
tailrec fun createNullCheck(searchCode: List<String>, fields: List<String>, nullCheck: String = ""): String {
    if(searchCode.isEmpty()) {
        return nullCheck
    }

    val nullString = fieldNullString(searchCode.joinToString("."), fields) ?: return createNullCheck(searchCode.subList(0, searchCode.size - 1), fields, nullCheck)
    val additionalCheck = fieldAdditionalChecks(searchCode.joinToString("."), fields) ?: ""

    return when {
        // "Y", Top-level field can be null, add a null check for the top level, then carry out a cascading null check
        nullString == "Y" -> createNullCheck(searchCode.subList(0, searchCode.size - 1), fields, "${searchCode.joinToString(".")} != null" + if(nullCheck != "") {" && "} else {""} + nullCheck)
        // "1;2;Y", Carry out a null check on certain parts of the field, including the top level, with a cascade
        nullString.contains("Y") -> createNullCheck(searchCode.subList(0, searchCode.size - 1), fields, createSplitNullCheck(searchCode.subList(0, searchCode.size - 1), nullString.split(";"), fields, "${searchCode.joinToString(".")} != null" + if(nullCheck != "") {" && "} else {""}) + "${searchCode.joinToString(".")} != null" + if(nullCheck != "") {" && "} else {""} + nullCheck)
        // "1;2L;3", Carry out a null check on certain parts of the field, where the field is split on "."
        nullString.split(";")[0].toIntOrNull() != null
                || (nullString.split(";")[0].isNotBlank()
                && nullString.split(";")[0].first().toString().toIntOrNull() != null) -> if(additionalCheck.isNotBlank()) { "$additionalCheck && " } else { "" } + createSplitNullCheck(searchCode.subList(0, searchCode.size), nullString.split(";"), fields, nullCheck)
        // "N", Entire field cannot be null
        else -> createNullCheck(searchCode.subList(0, searchCode.size - 1), fields, nullCheck)
    }
}

/**
 * Creates a null check for nulls split with ";"
 */
tailrec fun createSplitNullCheck(searchCode: List<String>, nullIndexes: List<String>, fields: List<String>, nullCheck: String): String {
    if (searchCode.isEmpty()) {
        return nullCheck
    }

    return createSplitNullCheck(searchCode.subList(0, searchCode.size - 1), nullIndexes, fields, (if(nullIndexes.contains((searchCode.size - 1).toString())) {
        "${searchCode.joinToString(".")} != null" + if(nullCheck != "") {" && "} else {""}
    } else if(nullIndexes.contains((searchCode.size - 1).toString() + "L")) {
        "${searchCode.joinToString(".")}.size() > 0" + if(nullCheck != "") {" && "} else {""}
    } else {""}) + nullCheck)
}

/*
tailrec fun createMultiNullCheck(inputFields: List<String>, allFields: List<String>, nullCheck: String = ""): String {
    if (inputFields.isEmpty()) {
        return nullCheck
    }

    val field = getFieldOrNull(inputFields[0], allFields, true)
    val fieldCode = if(field != null) field.split(",")[2] else null
    val localField = if(field != null) getFieldOrNull(field.split(",")[7].split("{", "}", ";")[0], allFields, true) else null
    val nextNullCheck = (if(localField != null) "${localField.split(",")[3].first().toLowerCase() + localField.split(",")[3].substring(1, localField.split(",")[3].length)} != null && " else "") + (if(fieldCode != null) createNullCheck(fieldCode.split("."), allFields) else "")

    return createMultiNullCheck2(inputFields.subList(1, inputFields.size), allFields, nullCheck + (if(nullCheck != "" && nextNullCheck != "") " && " else "") + nextNullCheck)
}
*/

/**
 * Creates multiple null checks, joined by &&
 *
 * @param inputFields
 */
fun createMultiNullCheck(inputFields: List<String>, allFields: List<String>, nullCheck: String = ""): String {
    if (inputFields.isEmpty()) {
        return nullCheck
    }

    val nextNullCheck = createComplexNullCheck(inputFields[0], allFields).joinToString(" && ")

    return createMultiNullCheck(inputFields.subList(1, inputFields.size), allFields, nullCheck + (if(nullCheck != "" && nextNullCheck != "") " && " else "") + nextNullCheck)
}

fun createComplexNullCheck(inputFields: String, allFields: List<String>) : List<String> {
    val fieldsIn = inputFields.split("[{]".toRegex(), 2)

    val field = getFieldOrNull(fieldsIn[0].replace("\r", "").trim('}', ' '), allFields)
    val wholeField = getFieldOrNull(fieldsIn[0].replace("\r", "").trim('}', ' '), allFields, true)

    val localField = if(wholeField != null && wholeField.split(",")[7].replace("\r", "") != "") getFieldOrNull(wholeField.split(",")[7].replace("\r", "").split(";", "{", "}")[0], allFields, true) else null
    val local = if(localField != null) listOf(localField.split(",")[3].first().toLowerCase() + localField.split(",")[3].substring(1, localField.split(",")[3].length) + " != null") else listOf()

    // Lone field; base case
    if(fieldsIn.size == 1) {
        return if(field != null) {
            val nullCheck = createNullCheck(field.split("."), allFields)
            local + if(nullCheck != "") listOf(nullCheck) else listOf()
        } else {
            local + listOf()
        }
    }

    // Field with parameters; continue recursion
    val parameters = splitParameters(fieldsIn[1].toCharArray().toList())

    return if(field == null) {
        // No field found, return blank
        listOf()
    } else{
        // Map field to code and process parameters through recursion, provided that the correct number of parameters have been provided
        if(getFieldOrNull(fieldsIn[0].trim(' '), allFields)!!.split("{P}").size == parameters.size + 1) {
            local + parameters.map { createComplexNullCheck(it, allFields) }.flatten() + (if(createNullCheck(field.split("."), allFields) != "") listOf(getFields(inputFields, allFields) + " != null") else listOf())
        } else{
            listOf()
        }
    }
}