

tailrec fun preprocess(csvIn: List<String>, csvOut: List<String> = listOf()) : List<String> {
    if(csvIn.isEmpty()) {
        return csvOut
    }

    val splitLine = csvIn[0].split(",")

    if(splitLine.size < 13 && csvIn.size > 2) {
        val newLineRemoved = listOf((csvIn[0] + csvIn[1]).replace("\r", ""))
        return preprocess(newLineRemoved + csvIn.subList(2, csvIn.size), csvOut)
    }

    return preprocess(csvIn.subList(1, csvIn.size),
        csvOut +
                listOf(csvIn[0]
        .replace("TRUE", "true")
        .replace("FALSE", "false")
        .replace("NULL", "null")))
}