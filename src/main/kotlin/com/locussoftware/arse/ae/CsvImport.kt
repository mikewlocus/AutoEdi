package com.locussoftware.arse.ae

import com.locussoftware.arse.ae.entities.SpecificationRow
import kotlin.math.max

const val MAX_FIELD_COUNT_LENGTH = 4;

tailrec fun getRowsFromCsv(csv: List<String>, specification_id: String, rows: List<SpecificationRow> = listOf()) : List<SpecificationRow> {
    if(csv.isEmpty()) {
        return rows
    }

    val splitLine = csv[0].split(",")
    val newRows = if(splitLine.size >= 14) {
        rows + listOf(
            SpecificationRow(
                null,
                specification_id,
                splitLine[0],
                splitLine[1],
                splitLine[2],
                splitLine[3],
                splitLine[4],
                splitLine[5],
                if(splitLine[6].length > 3) splitLine[6].substring(0, 3) else splitLine[6],
                splitLine[7],
                splitLine[8],
                splitLine[9],
                splitLine[10],
                splitLine[11],
                if(splitLine[12].length > MAX_FIELD_COUNT_LENGTH)
                    splitLine[12].substring(0, MAX_FIELD_COUNT_LENGTH)
                else
                    splitLine[12],
                splitLine[13],
                "",
                if(rows.isNotEmpty()) rows[rows.lastIndex].row_index?.plus(1) else 0
            )
        )
    } else {
        rows
    }

    return getRowsFromCsv(csv.subList(1, csv.size), specification_id, newRows)
}

tailrec fun preprocess(csvIn: List<String>, csvOut: List<String> = listOf()) : List<String> {
    if(csvIn.isEmpty()) {
        return csvOut
    }

    val splitLine = csvIn[0].split(",")

    if(splitLine.size < 13 && csvIn.size > 2) {
        val newLineRemoved = listOf((csvIn[0] + csvIn[1]).replace("\r", ""))
        return preprocess(newLineRemoved + csvIn.subList(2, csvIn.size), csvOut)
    }

    return preprocess(csvIn.subList(1, csvIn.size), csvOut + listOf(if(splitLine.size >= 13 && splitLine[11].isNotEmpty() && splitLine[11][0] == '"') { splitLine.subList(0, 11).joinToString(",") + "," + splitLine[11].substring(1, splitLine[11].length - 1) + "," + splitLine.subList(12, splitLine.size).joinToString(",") } else { csvIn[0] }
        .replace("\"\"", "\"")
        .replace("TRUE", "true")
        .replace("FALSE", "false")
        .replace("NULL", "null")))
}