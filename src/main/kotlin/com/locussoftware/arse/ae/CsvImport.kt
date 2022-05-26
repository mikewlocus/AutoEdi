package com.locussoftware.arse.ae

import com.locussoftware.arse.ae.entities.SpecificationRow
import kotlin.math.max

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