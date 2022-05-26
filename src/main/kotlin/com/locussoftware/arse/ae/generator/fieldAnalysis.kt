/*
This file contains functions for getting values from the fields table.
 */

/**
 * Searches the field list (imported from the variables table) for a certain variable. If found, returns either the
 * code for the field, or the entire row, depending on the wholeField parameter. If not found, returns null.
 *
 * @param searchField The field being searched for. This value will be used to match the variable name column.
 * @param fields The fields list imported from the variables table.
 * @param wholeField Optional boolean, tells the function to return the entire field row for a located field if true,
 * instead of just the code.
 * @return The code for the located field, the entire located field, or null.
 */
tailrec fun getFieldOrNull(searchField: String, fields: List<String>, wholeField: Boolean = false): String? {
    if(fields.isEmpty()) {
        // Base case; field not found, return null
        return null
    }

    // Get the first field in the list
    val field = fields[0].split(",")
    // Trim and clean search field
    val sField = searchField.trim(' ').replace("\r", "")

    // Check for a match in the fields table
    if(field.size > FIELD_VARIABLE_COLUMN && field[FIELD_VARIABLE_COLUMN] == sField) {
        return if(wholeField)
            // Return the whole field, rejoined by commas
            field.joinToString(",")
        else
            // Return the code
            field[FIELD_CODE_COLUMN].replace(";", ",")
    }

    // Field not found this time; continue recursion with a smaller list
    return getFieldOrNull(sField, fields.subList(1, fields.size), wholeField)
}

/**
 * Searches the field list for a certain variable, and creates a local variable if the field requires it.
 *
 * @param searchField The field being searched for. This value will be used to match the variable name column.
 * @param fields The fields list imported from the variables table.
 * @param allFields The fields list again, allowing for the search to reset upon finding the variable which requires a
 * local.
 * @return The code for the local variable of the located field, or null if the field is not found, or if the local
 * variable is not found.
 */
tailrec fun getLocalOrNull(searchField: String, fields: List<String>, allFields: List<String> = fields): String? {
    if(fields.isEmpty()) {
        // Base case; field not found, return null
        return null
    }

    // Get the first field in the list
    val field = fields[0].split(",")
    // Trim and clean search field
    val sField = searchField.trim(' ')

    // Check for a match in the fields table
    if(field.size > FIELD_VARIABLE_COLUMN && field[FIELD_VARIABLE_COLUMN] == sField) {
        // Get the whole field for the local variable
        val fieldForLocal = getFieldOrNull(field[FIELD_LOCALS_COLUMN].split("{")[0], allFields, true)

        return if(fieldForLocal != null) {
            val varType = fieldForLocal.split(",")[FIELD_TYPE_COLUMN]
            val varName = varType.first().toLowerCase() + varType.substring(1)
            val ternCondition = createMultiNullCheck(listOf(field[FIELD_LOCALS_COLUMN]), allFields)
            val ternCode = getFields(field[FIELD_LOCALS_COLUMN], allFields)

            // Return local variable definition, with null checks in a ternary operator
            "$varType $varName = ($ternCondition) ? $ternCode : null"
        } else {
            // No local found
            null
        }
    }

    return getLocalOrNull(sField, fields.subList(1, fields.size), allFields)
}

/**
 * Searches for the field in the fields table, and returns its null check values if found. Returns null if field is not
 * found.
 */
fun fieldNullString(searchCode: String, fields: List<String>): String? {
    return getFieldValueAtColumnUsingCodeToSearch(searchCode, fields, FIELD_NULL_COLUMN)
}

/**
 * Searches for the field in the fields table, and returns its additional check values if found. Returns null if field
 * is not found.
 */
fun fieldAdditionalChecks(searchCode: String, fields: List<String>): String? {
    return getFieldValueAtColumnUsingCodeToSearch(searchCode, fields, FIELD_ADDITIONAL_COLUMN)
}

/**
 * Searches the fields list on the code column, returning the value of the required column if found. Returns null if
 * not found.
 *
 * @param searchCode The code being searched for. This value will be used to match with the code column.
 * @param fields The fields list imported from the variables table.
 * @param column The column which contains the value to be returned if the search variable is found.
 */
tailrec fun getFieldValueAtColumnUsingCodeToSearch(searchCode: String, fields: List<String>, column: Int) : String? {
    // Base case; field not found
    if(fields.isEmpty()) return null

    // Get the first row
    val field = fields[0].split(",")

    // Check if this row matches the code being searched for
    if(field.size > FIELD_CODE_COLUMN && field[FIELD_CODE_COLUMN] == searchCode) {
        // Return the value from the required column
        return field[column]
    }

    // Not found this time; carry on recursion
    return getFieldValueAtColumnUsingCodeToSearch(searchCode, fields.subList(1, fields.size), column)
}