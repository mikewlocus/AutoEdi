/*
This file contains methods for analysing properties of the input CSV's data
 */

/**
 * Returns true if a segment has no information in it, in which case it doesn't need to be processed
 */
tailrec fun checkSectionIsEmpty(sectionLines: List<String>): Boolean {
    if(sectionLines.isEmpty()) {
        return true
    }

    val currentLine = sectionLines[0].split(",")

    return when {
        currentLine.size < 8
                || currentLine[7] != ""
                || currentLine[8] == "" -> true
        currentLine.size > 10 && currentLine[11] != "---" -> false
        else -> checkSectionIsEmpty(sectionLines.subList(1, sectionLines.size))
    }
}

/**
 * Searches along the first '|' line from the left. Returns the line of the end of the segment group, found where it encounters the first "-"
 */
tailrec fun segmentGroupLength(sheetLines: List<String>, column: Int, count: Int = 0): Int {
    return if(sheetLines.isEmpty() || column >= sheetLines[0].split(",").size || sheetLines[0].split(",")[column] == "-") {
        count
    } else{
        segmentGroupLength(sheetLines.subList(1, sheetLines.size), column, count + 1)
    }
}

/**
 * Searches for the first occurrence of "+" or !"" for primary segments outside of a group. Returns the line of the end of the segment group, found where it encounters the first "+" or !""
 */
tailrec fun looseSegmentLength(sheetLines: List<String>, count: Int = 0): Int {
    return if(sheetLines.isEmpty() || sheetLines[0].split(",").size < 6 || sheetLines[0].split(",")[5] == "+" || sheetLines[0].split(",")[6] != "") {
        count
    } else{
        looseSegmentLength(sheetLines.subList(1, sheetLines.size), count + 1)
    }
}

/**
 * Searches for the next occurrence of data in a column
 */
tailrec fun nextNonBlankCell(sheetLines: List<String>, column: Int, count: Int = 0): Int {
    return if(sheetLines.isEmpty() || sheetLines[0].split(",").size <= column || sheetLines[0].split(",")[column].isNotBlank()) {
        count
    } else{
        nextNonBlankCell(sheetLines.subList(1, sheetLines.size), column,count + 1)
    }
}

/**
 * Searches along the primary segment column. Returns the number of lines before the next primary segment.
 */
tailrec fun segmentLength(sheetLines: List<String>, count: Int = 0): Int {
    return if(sheetLines.isEmpty() || sheetLines[0].split(",")[6] == "-") {
        count
    } else{
        segmentGroupLength(sheetLines.subList(1, sheetLines.size), count + 1)
    }
}

/**
 * Searches along a row for the first occurrence of a '+'. Returns the column position, indicating the depth at which a segment group exists.
 */
tailrec fun plusColumn(currentLine: List<String>, currentColumn: Int = 1): Int {
    return if(currentLine.isEmpty() || currentColumn == currentLine.size || currentLine[currentColumn] == "+") {
        currentColumn
    } else{
        plusColumn(currentLine, currentColumn + 1)
    }
}

/**
 * Determines whether an end of segment line "------,-,-,..." is the end of a subsegment (returns false) or the end of a top-level segment (returns true)
 */
tailrec fun endOfTopLevelSegment(currentLine: List<String>): Boolean {
    return if(currentLine.isEmpty()) {
        true
    } else if(currentLine[0] == "+" || currentLine[0] == "|") {
        false
    } else{
        endOfTopLevelSegment(currentLine.subList(1, currentLine.size))
    }
}

fun isNotUNSegment(segmentCode: String): Boolean {
    return segmentCode != "UNB" && segmentCode != "UNH" && segmentCode != "UNT" && segmentCode != "UNZ"
}

/**
 * Performs a linear search on the CSV, searching for the given element name. Returns the value associated with that
 * element if found, or a blank string if not.
 *
 * @param sheetLines The CSV lines.
 * @param elementName The name of the element.
 * @return Either the value of the found element, or a blank string.
 */
tailrec fun getValueForElement(sheetLines: List<String>, fields: List<String>, elementName: String): String {
    return if(sheetLines.isEmpty() || sheetLines[0].split(",").size <= VALUE_COLUMN) {
        // Not found
        "\"\""
    } else {
        val currentLine = sheetLines[0].split(",")

        if(elementName == currentLine[ELEMENT_ID_COLUMN].trim()
            || elementName == currentLine[ELEMENT_SUB_ID_COLUMN].trim()) {
            // Found
            if(currentLine[VALUE_COLUMN] == "---") {
                "\"\""
            } else {
                if(currentLine[VALUE_COLUMN].isNotEmpty() && currentLine[VALUE_COLUMN].toCharArray()[0] == '[') {
                    val splitCond = currentLine[VALUE_COLUMN].split("[", "]")

                    createConditional(splitCond.subList(1, splitCond.size), "", fields, inline = true)
                } else {
                    getFields(currentLine[VALUE_COLUMN], fields)
                }
            }
        } else if(elementName == currentLine[SEGMENT_COLUMN].trim()) {
            // Found segment conditional
            when(currentLine[VALUE_COLUMN]) {
                "" -> "true"
                "DO NOT CREATE" -> "false"
                else -> getFields(currentLine[VALUE_COLUMN], fields)
            }
        } else {
            // Carry on searching
            getValueForElement(sheetLines.subList(1, sheetLines.size), fields, elementName)
        }
    }
}