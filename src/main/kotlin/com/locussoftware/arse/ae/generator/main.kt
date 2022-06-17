import com.locussoftware.arse.ae.ErrorCode
import com.locussoftware.arse.ae.GeneratorResult
import com.locussoftware.arse.ae.generator.validateSquareBrackets
import java.io.BufferedReader
import java.io.File

var errors: HashMap<Int, Int> = hashMapOf()
var rowCount: Int = 0

/**
 * @author Mike Wayne
 */
fun generator(csv: String, fileName: String, fieldsCsv: String) : GeneratorResult {

    errors = hashMapOf()

    val fileNameSplit = fileName.split(".")
    val fileNameSplitUnderscores = fileName.split("_")

    // Validate file (CSV, Name_Standard)
    if(fileNameSplitUnderscores.size >= 2) {

        // Get values from file
        val inputCsv = csv

        val messageType = fileName[0].toUpperCase() + fileNameSplitUnderscores[0].substring(1).toLowerCase()
        val version = fileNameSplitUnderscores[1].toUpperCase().split(".")[0]
        val standard = when (version) {
            "D95B" -> "D95B"
            "D16A" -> "D16A"
            else -> "D00B"
        }
        val identifier = if(fileNameSplitUnderscores.size > 2) { fileNameSplitUnderscores[2].split(".")[0] } else { "" }

        // Split and clean file content
        val sheetLines = preprocess(inputCsv.split(System.lineSeparator()))
        val fields = fieldsCsv
                .replace("\"\"", "&dQuot1653061221")
                .replace("\"", "")
                .replace("&dQuot1653061221", "\"")
                .split(System.lineSeparator())

        println("Preprocessed input: \n${sheetLines.joinToString("\n")}\n")

        // Build file from generated code
        val code = generateEdiCode(sheetLines, fields, standard)
        val creator = segCounters(
            sheetLines,
            standard
        ) + "protected $messageType create${messageType}(@Nonnull IntegrationMessageLog integrationMessageLog, ${
            getCreatorParams(
                messageType
            ).joinToString(", ") { "@Nonnull $it" }
        }) throws IllegalAccessException, OdysseyException {${System.lineSeparator()}\t${messageType} ${messageType.toLowerCase()} = new ${messageType}();${System.lineSeparator()}" + generateCreatorFunction(
            messageType,
            sheetLines,
            fields,
            standard
        )

        // Output generated code
        val generated = generateImports(sheetLines, messageType, version) + createClassDeclaration(
            messageType,
            version,
            identifier
        ) + createHeaderMethod(messageType, version, sheetLines, fields) + creator + code + createInterfaceMethods(messageType) + "\n}"
        val postProcessed = generated
            .replace("(undg)", "UNDG") // Handle unconventional UNDG name
            .replace("UndgNumber", "UNDGNumber") // Handle unconventional UNDG name II
            .replace("setEmsNumber", "setEMSNumber") // Handle unconventional EMS name
            .replace("  ", " ")
            .split("(?<=(}|== \"\"|==\"\"|!= \"\"|!=\"\"|&&|\\|\\||&& \\(|&&\\(|\\|\\| \\(|if\\()|if \\()|(?=(}|== \"\"|==\"\"|!= \"\"|!=\"\"|&&|\\|\\||&& \\(|\\|\\| \\(|if\\()|if \\()".toRegex())
            .zipWithNext { a, b ->
                when (b) {
                    "!= \"\"", "!=\"\"" -> "StringUtils.isNotBlank($a)"
                    "== \"\"", "==\"\"" -> "StringUtils.isBlank($a)"
                    else -> when (a) {
                        "!= \"\"", "== \"\"", "!=\"\"", "==\"\"" -> ""
                        else -> a
                    }
                }
            }
            .joinToString("") // Replace raw string comparison with StringUtils ('}' added to split, as zipWithNext ignores the final element)

        println(postProcessed)

        val file = File("Process${messageType}${version}$identifier.java")

        // Create new file if it doesn't already exist
        if(!file.exists()) {
            file.createNewFile()
        }

        // Write output to file
        File("Process${messageType}${version}$identifier.java").printWriter().use { out ->
            out.println(postProcessed)
        }

        return GeneratorResult("Process${messageType}${version}$identifier.java", errors)
    }

    return GeneratorResult("", errors)
}


/**
 * The main generator function for the message class. This function analyses the input CSV line-by-line, in order to produce the body of the message class.
 */
tailrec fun generateEdiCode(sheetLines: List<String>,
                            fields: List<String>,
                            standard: String,
                            currentCode: String = "",
                            currentFunctions: String = "",
                            currentVariable: String = "",
                            currentSegmentVariable: String = "",
                            currentGroup: String = "0",
                            endOfSegmentCode: String = ""): String {

    // End of file
    if(sheetLines.isEmpty()) {
        // Return the generated code. If the function is an offshoot for handling a segment group, then also write end-of-function code (return sgX; \n })
        return currentCode + (if(currentSegmentVariable != "" && currentGroup != "0") "\t}${System.lineSeparator()}" else "") + System.lineSeparator() + (if(currentGroup != "0") "\treturn sg${currentGroup.split("_")[0]};${System.lineSeparator()}}" else "") + "${System.lineSeparator()}$currentFunctions"
    }

    println("Processing line: " + sheetLines[0])

    // Get an array of the cells in the current row
    val currentLine = sheetLines[0].split(",")

    // Set the row counter to the current row's index
    rowCount = currentLine[INDEX_COLUMN].toInt()

    // Error message due to improper formatting of the CSV file (all rows should have at least 12 cells, if they don't, it's an indication of the presence of a carriage return in a cell)
    if(currentLine.size < 13) {
        throw error (currentCode + currentFunctions + "ERROR: The CSV file contains a formatting error, encountered here. This can be caused by a line separator within a cell.")
    }

    if(!validateSquareBrackets(currentLine[VALUE_COLUMN])) {
        errors[rowCount] = ErrorCode.SQUARE_BRACKET_ERROR.code
    }

    // Segment group creation methods
    if(currentLine[0] != "" && currentLine[0].length > 2) {
        // If the first cell contains a value that isn't "------", it indicates the beginning of a new segment group
        if(currentLine[0] != "------") {
            // Find information about the segment group
            val segGroupLength = segmentGroupLength(sheetLines.subList(1, sheetLines.size), plusColumn(currentLine))
            val segGroupIn = currentLine[0].substring(2)

            // Add incrementing appendage to method name if it already exists
            val segGroup = when {
                currentGroup.split("_").size > 1 -> segGroupIn + "_" + currentGroup.split("_").subList(1, currentGroup.split("_").size).joinToString("_") + "_" + currentCode.split("createSegmentGroup${segGroupIn}").size
                currentCode.split("createSegmentGroup${segGroupIn}").size > 1 -> segGroupIn + "_" + (currentCode.split("createSegmentGroup${segGroupIn}").size - 1)
                else -> segGroupIn
            }

            val sgNum = segGroup.split("_")[0]

            val loopLogic = currentLine[13].replace("\r", "").split(">")
            val loopCode = if(loopLogic.size > 1) {
                generateLoops(loopLogic)
            } else {
                generateEntityVariables(loopLogic[0].split(";"))
            }
            val loopClosing = if(loopLogic.size > 1) {
                loopLogic.subList(1, loopLogic.size).joinToString("") { "\t}$endOfSegmentCode${System.lineSeparator()}" }
            } else if(loopCode.isNotEmpty()) {
                val splitOnIfs = loopCode.split("if(true)")
                splitOnIfs.subList(1, splitOnIfs.size).joinToString("") { "\t}$endOfSegmentCode${System.lineSeparator()}" }
            } else {
                ""
            }

            val newCode = if(currentLine[12].toIntOrNull() != null && currentLine[12].toInt() > 1) {
                // // SGXs
                // List<SegmentGroupX> sgXs = new ArrayList<>();
                // sgY.setSegmentGroupX(sgXs);
                // sgXs.add(createSegmentGroupX());
                currentCode + (if(currentSegmentVariable != "" && currentGroup != "0") "\t}$endOfSegmentCode${System.lineSeparator()}" else "") + if(currentGroup.split("_")[0].toInt() > 0) if(!currentCode.contains("List<SegmentGroup${sgNum}>")) { "${System.lineSeparator()}\t// SG${sgNum}s${System.lineSeparator()}\tList<SegmentGroup${sgNum}> sg${sgNum}s = new ArrayList<>();${System.lineSeparator()}\tsg${currentGroup.split("_")[0]}.setSegmentGroup${sgNum}(sg${sgNum}s);${System.lineSeparator()}" } else { "" } + "\tint count${segGroup} = 0;${System.lineSeparator()}" + loopCode + "\tsg${sgNum}s.add(createSegmentGroup${segGroup}" + "(${(createParameters(sheetLines.subList(1, segGroupLength), fields) + listOf("  ++count${segGroup}")).joinToString(", "){ it.split(" ")[2] }}));${System.lineSeparator()}" + loopClosing else ""
            } else {
                // // SGX
                // SegmentGroupX sgX = createSegmentGroupX()
                currentCode + (if(currentSegmentVariable != "" && currentGroup != "0") "\t}$endOfSegmentCode${System.lineSeparator()}" else "") + if(currentGroup.split("_")[0].toInt() > 0) "${System.lineSeparator()}\t// SG${sgNum}${System.lineSeparator()}\tSegmentGroup${sgNum} sg${sgNum} = createSegmentGroup${segGroup}(${(createParameters(sheetLines.subList(1, segGroupLength), fields) + listOf("  1")).joinToString(", "){ it.split(" ")[2] }});${System.lineSeparator()}\tsg${currentGroup.split("_")[0]}.setSegmentGroup${sgNum}(sg${sgNum});${System.lineSeparator()}" + loopClosing else ""
            }

            // Pass the new segment group into a new recursive branch, in order to add the new function to the the main body of the class
            // protected SegmentGroupX createSegmentGroupX() {
            //     SegmentGroupX sgX = new SegmentGroupX();
            //     ...generateEdiCode()...
            val newFunctions = currentFunctions + "protected SegmentGroup${sgNum} createSegmentGroup${segGroup}(${(createParameters(sheetLines.subList(1, segGroupLength), fields) + listOf("int count")).joinToString(", ")}) throws OdysseyException {${System.lineSeparator()}\tSegmentGroup${sgNum} sg${sgNum} = new SegmentGroup${sgNum}();${System.lineSeparator()}" + generateEdiCode(sheetLines.subList(1, segGroupLength + 1), fields, standard, currentGroup = segGroup)

            return generateEdiCode(
                sheetLines.subList(1 + segGroupLength, sheetLines.size),
                fields,
                standard,
                newCode,
                newFunctions,
                currentGroup = currentGroup
            )
        } else {
            // Check for end of primary segment group, add all functions stored in parameter
            if(endOfTopLevelSegment(currentLine.subList(1, currentLine.size))) {
                return generateEdiCode(
                    sheetLines.subList(1, sheetLines.size),
                    fields,
                    standard,
                    currentCode + System.lineSeparator() + currentFunctions,
                    currentGroup = currentGroup
                )
            }
        }
    }

    // Primary segment creation
    if(currentLine[6] != "" && currentLine[11] != "DO NOT CREATE" && !currentLine[6].contains("SEG")) {
        // Skip over UN segments
        if(!isNotUNSegment(currentLine[6])) {
            return generateEdiCode(sheetLines.subList(nextNonBlankCell(sheetLines.subList(1, sheetLines.size), 6) + 1, sheetLines.size), fields, standard, currentCode, currentFunctions, currentVariable, currentSegmentVariable, currentGroup)
        }

        // Create creation conditional
        val conditional = if(currentGroup != "0") {
            if(currentLine[11].isNotEmpty() && currentLine[11].toCharArray()[0] == '[') {
                createConditionWithChecks(currentLine[11].trim('[', ']').split("(?<=(&&|\\|\\|))|(?=(&&|\\|\\|))".toRegex()), fields)
            } else{
                "true"
            }
        } else{
            ""
        }

        // Create loose method definition
        val methodDef = if(currentGroup == "0") {
            "${System.lineSeparator()}protected ${createObjectType(currentLine[6], currentLine[10], standard)} create${currentLine[6]}(${(createParameters(sheetLines.subList(1, looseSegmentLength(sheetLines.subList(1, sheetLines.size))), fields) + listOf("int count")).joinToString(", ")}) {"
        } else{
            ""
        }

        // Create looping
        val loopCount = currentLine[LOOP_COLUMN].replace("\r", "")
        val loopCode = if(loopCount.toIntOrNull() != null) {
            "\tfor (int loopCount = 0; loopCount < ${loopCount.toInt()}; loopCount++) {\n"
        } else {
            ""
        }

        // Subsegment instantiation
        val newCode = if(currentLine[12].toIntOrNull() != null && currentLine[12].toInt() > 1 && currentGroup != "0") {
            // List<XYZAbc> xyzs = new ArrayList<>();
            // sgX.setXYZAbc(xyzs);
            // XYZAbc xyz = new XYZAbc();
            // xyzs.add(xyz);
            currentCode + (if(currentSegmentVariable != "" && currentGroup != "0") "\t}$endOfSegmentCode${System.lineSeparator()}" else "") + methodDef + "${System.lineSeparator()}\t// ${currentLine[6]}${System.lineSeparator()}" + if(!currentCode.contains("List<${createObjectType(currentLine[6], currentLine[10], standard)}>")) { "\tList<${createObjectType(currentLine[6], currentLine[10], standard)}> ${currentLine[6].toLowerCase()}s = new ArrayList<>();${System.lineSeparator()}\tsg${currentGroup.split("_")[0]}.set${createObjectType(currentLine[6], currentLine[10], standard)}(${currentLine[6].toLowerCase()}s);${System.lineSeparator()}"} else {""} + loopCode + (if(conditional != "") "\tif(${conditional}) {${System.lineSeparator()}" else "") + "\t${createSegmentObject(currentLine[6], currentLine[10], standard)}${System.lineSeparator()}\t${currentLine[6].toLowerCase()}s.add(${currentLine[6].toLowerCase()}" + ");${System.lineSeparator()}"
        } else{
            // XYZAbc xyz = new XYZAbc();
            // sgX.setXYZAbc(xyz);
            currentCode + (if(currentSegmentVariable != "" && currentGroup != "0") "\t}$endOfSegmentCode${System.lineSeparator()}" else "") + methodDef + "${System.lineSeparator()}\t// ${currentLine[6]}${System.lineSeparator()}" + loopCode + (if(conditional != "") "\tif(${conditional}) {${System.lineSeparator()}" else "") + "\t${createSegmentObject(currentLine[6], currentLine[10], standard)}${System.lineSeparator()}" + (if(currentGroup != "0") "\tsg${currentGroup.split("_")[0]}.set${createObjectType(currentLine[6], currentLine[10], standard)}(${currentLine[6].toLowerCase()});${System.lineSeparator()}" else "")
        }

        return generateEdiCode(sheetLines.subList(1, sheetLines.size),
            fields,
            standard,
            newCode,
            currentFunctions,
            currentSegmentVariable = currentLine[6].toLowerCase(),
            currentGroup = currentGroup,
            endOfSegmentCode = if(loopCode.isNotBlank()) "}" else "")
    }

    // End of loose method
    val endOfLooseMethod = if((currentLine[7] != "" || currentLine[8] != "") && looseSegmentLength(sheetLines.subList(1, sheetLines.size)) == 0 && currentGroup == "0") {
        "${System.lineSeparator()}\treturn $currentSegmentVariable;${System.lineSeparator()}}${System.lineSeparator()}"
    } else{
        ""
    }

    // Secondary segment creation
    if(currentLine[7] != "") {
        // Check whether the current secondary segment has a number
        if(currentLine[7][0] == 'C') {
            if(!checkSectionIsEmpty(sheetLines.subList(1, sheetLines.size)))
                return generateEdiCode(sheetLines.subList(1, sheetLines.size),
                    fields,
                    standard,
                    currentCode + "\t${createSecondarySegmentObject(currentLine[7], currentLine[10], standard)}${System.lineSeparator()}\t${currentSegmentVariable}.set${createObjectType(currentLine[7], currentLine[10], standard)}${currentLine[9]}(${currentLine[7].toLowerCase()});${System.lineSeparator()}" + endOfLooseMethod,
                    currentFunctions,
                    currentLine[7].toLowerCase(),
                    currentSegmentVariable,
                    currentGroup,
                    endOfSegmentCode)
        }
    }

    // Tertiary segment population
    if(((currentLine[7] != "" && currentLine[7][0] != 'C') || currentLine[8] != "") && currentLine[11] != "---") {

        // Check for conditional syntax
        if(currentLine[11].isNotEmpty() && currentLine[11].toCharArray()[0] == '[') {
            val splitCond = currentLine[11].split("[", "]")
            val conditional = createConditional(splitCond.subList(1, splitCond.size), if(currentLine[7] != "") { currentSegmentVariable } else { currentVariable } + ".set${createObjectType(currentLine[8], currentLine[10], standard)}${currentLine[9]}(" + createTypeParse(currentLine[12]), fields, fieldCloser = if(createTypeParse(currentLine[12]) != "") ")" else "")

            return generateEdiCode(sheetLines.subList(1, sheetLines.size),
                fields,
                standard,
                currentCode + conditional + System.lineSeparator() + endOfLooseMethod,
                currentFunctions,
                currentVariable,
                currentSegmentVariable,
                currentGroup,
                endOfSegmentCode)
        }

        val field = getFields(currentLine[11], fields)
        val locals = createLocals(currentLine[11].split(";", "{", "}"), fields)
        val nullCheck = createMultiNullCheck(currentLine[11].split(";", "{", "}"), fields)

        if(field == currentLine[VALUE_COLUMN] && field.contains("$")) {
            errors[rowCount] = ErrorCode.VARIABLE_NOT_FOUND_ERROR.code
        }

        val subStringCode = if(currentLine[9].isNotBlank()) {
            val componentLength = currentLine[TYPE_COLUMN].toIntOrNull() ?: getComponentLength(standard)
            val lowerBound = (Integer.valueOf(currentLine[9]) - 1) * componentLength
            val upperBound = Integer.valueOf(currentLine[9]) * componentLength
            ".replace(\"\\r\\n\", \" \").substring($lowerBound < String.valueOf($field.replace(\"\\r\\n\", \" \")).length() ? $lowerBound : 0, $upperBound < String.valueOf($field.replace(\"\\r\\n\", \" \")).length() ? $upperBound : (String.valueOf($field.replace(\"\\r\\n\", \" \")).length() > $lowerBound ? String.valueOf($field.replace(\"\\r\\n\", \" \")).length() : 0))"
        } else {
            ""
        }

        // Return fields, with null check if necessary
        return generateEdiCode(sheetLines.subList(1, sheetLines.size),
            fields,
            standard,
            currentCode + (if(locals != "") "\t$locals" else "") + if(nullCheck != "") {"\tif(${nullCheck}) {${System.lineSeparator()}\t"} else {""} + "\t" + if(currentLine[7] != "") { currentSegmentVariable } else { currentVariable } + ".set${createObjectType(currentLine[8], currentLine[10], standard)}${currentLine[9]}(" + createTypeParse(currentLine[12]) + field + (if(createTypeParse(currentLine[12]) != "") ")" else "") + subStringCode + ");${System.lineSeparator()}" + (if(nullCheck != "") {"\t}${System.lineSeparator()}"} else {""}) + endOfLooseMethod,
            currentFunctions,
            currentVariable,
            currentSegmentVariable,
            currentGroup,
            endOfSegmentCode)
    }

    // Skip irrelevant sections
    if(currentLine[11] == "DO NOT CREATE") {
        return generateEdiCode(sheetLines.subList(looseSegmentLength(sheetLines.subList(1, sheetLines.size)) + 1, sheetLines.size), fields, standard, currentCode, currentFunctions, currentVariable, currentSegmentVariable, currentGroup, endOfSegmentCode)
    }

    // No relevant information
    return generateEdiCode(sheetLines.subList(1, sheetLines.size), fields, standard, currentCode + endOfLooseMethod, currentFunctions, currentVariable, currentSegmentVariable, currentGroup, endOfSegmentCode)
}