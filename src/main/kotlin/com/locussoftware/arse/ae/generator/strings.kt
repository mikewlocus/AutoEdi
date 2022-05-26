/*
This file contains functions which return strings containing formatted code
*/

/**
 * Creates code for new objects: Object object = new Object();
 */
fun createSegmentObject(segmentCode: String, segmentName: String, standard: String, increment: String = ""): String {
    return when(standard) {
        "D16A" -> """${createObjectType(segmentCode, segmentName, standard)} ${segmentCode.toLowerCase()}${increment} = new ${createObjectType(segmentCode, segmentName, standard)}();"""
        "D00B" -> """${createObjectType(segmentCode, segmentName, standard)} ${segmentCode.toLowerCase()}${increment} = new ${createObjectType(segmentCode, segmentName, standard)}();"""
        "D95B" -> """${createObjectType(segmentCode, segmentName, standard)} ${segmentCode.toLowerCase()}${increment} = new ${createObjectType(segmentCode, segmentName, standard)}();"""
        else -> "STANDARD_NOT_SUPPORTED"
    }
}

/**
 * Creates code for secondary segment objects: XYZAbc xyz = new XYZAbc();
 */
fun createSecondarySegmentObject(segmentCode: String, segmentName: String, standard: String, increment: String = ""): String {
    return when(standard) {
        "D16A" -> createSegmentObject(segmentCode, segmentName, standard, increment)
        "D00B" -> """${toCamelCase(segmentName.split("/", " ", "-", "."))}${segmentCode} ${segmentCode.toLowerCase()}${increment} = new ${toCamelCase(segmentName.split("/", " ", "-"))}${segmentCode}();"""
        "D95B" -> """${toCamelCase(segmentName.split("/", " ", "-", "."))}${segmentCode} ${segmentCode.toLowerCase()}${increment} = new ${toCamelCase(segmentName.split("/", " ", "-"))}${segmentCode}();"""
        else -> "STANDARD_NOT_SUPPORTED"
    }
}

/**
 * Creates object type: XYZAbc
 */
fun createObjectType(segmentCode: String, segmentName: String, standard: String): String {
    return when(standard) {
        "D16A" -> """${segmentCode}${toCamelCase(segmentName.split("/", " "))}"""
        "D00B" -> toCamelCase(segmentName.split("/", " ", "-", "."))
        "D95B" -> toCamelCase(segmentName.split("/", " ", "-", "."))
        else -> "STANDARD_NOT_SUPPORTED"
    }
}

/**
 * Returns the input strings, joined together into one, and as camelcase
 */
fun toCamelCase(input: List<String>): String {
    // Get rid of trailing spaces
    if(input.isNotEmpty() && input[input.size - 1] == "") {
        return toCamelCase(input.subList(0, input.size - 1))
    }

    return input.joinToString("") {
        if(it == "") {
            ""
        } else {
            it[0].toUpperCase() + it.substring(1).toLowerCase()
        }
    }
}

/**
 * Returns the code to parse a value to a certain type, based on the input string
 */
fun createTypeParse(type: String = "") : String {
    return when(type) {
        "S" -> "String.valueOf("
        "B" -> "new BigDecimal("
        else -> ""
    }
}

fun getComponentLength(standard: String): Int {
    return when(standard) {
        "D16A" -> 512
        "D00B" -> 512
        "D95B" -> 70
        else -> 70
    }
}