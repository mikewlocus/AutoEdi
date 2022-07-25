import com.locussoftware.arse.ae.ErrorCode
import com.locussoftware.arse.ae.GeneratorResult
import com.locussoftware.arse.ae.generator.validateComparators
import com.locussoftware.arse.ae.generator.validateRoundBrackets
import com.locussoftware.arse.ae.generator.validateSquareBrackets
import java.io.BufferedReader
import java.io.File

var errors: HashMap<Int, Int> = hashMapOf()
var rowCount: Int = 0

/**
 * The entry point for the EDI message class generator.
 *
 * @param csv The input specification, as a CSV string.
 * @param fileName The filename for the output message class.
 * @param fieldsCsv The variables table, as a CSV string.
 * @return A generator result, containing the generated file path and errors encountered in generation.
 * @author Mike Wayne
 */
fun generator(csv: String, fileName: String, fieldsCsv: String, standards: String) : GeneratorResult {

    // Reset the error map with every generation
    errors = hashMapOf()

    val fileNameSplit = fileName.split(".")
    val fileNameSplitUnderscores = fileName.split("_")

    // Validate file name is Name_Standard_..., e.g. COPARN_D95B
    if(fileNameSplitUnderscores.size >= 2) {
        // Get the message type from the file name
        val messageType = fileName[0].uppercase() + fileNameSplitUnderscores[0].substring(1).lowercase()
        // Get the message version from the second split of the file name
        val version = fileNameSplitUnderscores[1].uppercase().split(".")[0]
        // Set the message standard based on the version; this is to handle Smooks' different naming conventions
        val standard = when (version) {
            "D95B" -> "D95B"
            "D16A" -> "D16A"
            else -> "D00B"
        }
        // The "name" of the specification, often the company who the message class is used to communicate with.
        val identifier = if(fileNameSplitUnderscores.size > 2) { fileNameSplitUnderscores[2].split(".")[0] } else { "" }

        // Split and clean file content
        val sheetLines = preprocess(csv.split(System.lineSeparator()))

        // Sort out speech marks in fields CSV
        val quoteString = "&dQuot1653061221"
        val fields = fieldsCsv
                .replace("\"\"", quoteString)
                .replace("\"", "")
                .replace(quoteString, "\"")
                .split(System.lineSeparator())

        // Build file from generated code
        val code = generateEdiCode(sheetLines, fields, standard)
        // Generate top-level creator function, with segment counters and function definition
        val segCounters = segCounters(
            sheetLines,
            standard
        )
        val functionDefinition = "protected $messageType create${messageType}(@Nonnull IntegrationMessageLog integrationMessageLog, ${
            getCreatorParams(
                messageType
            ).joinToString(", ") { "@Nonnull $it" }
        }) throws IllegalAccessException, OdysseyException {${System.lineSeparator()}" +
                "\t${messageType} ${messageType.toLowerCase()} = new ${messageType}();${System.lineSeparator()}"

        val creatorBody = generateCreatorFunction(
            messageType,
            sheetLines,
            fields,
            standard
        )
        val creator = segCounters + functionDefinition + creatorBody

        // Output generated code
        val imports = generateImports(sheetLines, messageType, version) + createClassDeclaration(
            messageType,
            version,
            identifier
        )
        val headerMethod = createHeaderMethod(messageType, version, standard, sheetLines, fields)
        val interfaceMethods = createInterfaceMethods(messageType)
        val classCloser = "\n}"
        val generated = imports + headerMethod + creator + code + interfaceMethods + classCloser

        // Postprocess the code, to remove any generation inefficiencies
        val conditionalSplitRegex = "(?<=(}|== \"\"|==\"\"|!= \"\"|!=\"\"|== \"|==\"|!= \"|!=\"|&&|\\|\\||&& \\(|&&\\(|\\|\\| \\(|if\\()|if \\(|\\) \\{)|(?=(}|== \"\"|==\"\"|!= \"\"|!=\"\"|== \"|==\"|!= \"|!=\"|&&|\\|\\||&& \\(|\\|\\| \\(|if\\()|if \\(|\\) \\{)".toRegex()
        val postProcessed = joinStringsWithEquals(generated
            .replace("(undg)", "UNDG") // Handle unconventional UNDG name
            .replace("UndgNumber", "UNDGNumber") // Handle uncapitalised UNDG number
            .replace("setEmsNumber", "setEMSNumber") // Handle uncapitalised EMS number
            .replace("  ", " ") // Get rid of double spaces
            .split(conditionalSplitRegex)
        )
                // Handle replacement of string comparators
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
            .joinToString("")

        // Save the output file
        val outputFilePath = "Process${messageType}${version}$identifier.java"
        val file = File(outputFilePath)

        // Create new file if it doesn't already exist
        if(!file.exists()) {
            file.createNewFile()
        }

        // Write output to file
        File(outputFilePath).printWriter().use { out ->
            out.println(postProcessed)
        }

        return GeneratorResult(outputFilePath, errors)
    }

    // Generator passed improperly formatted values TODO: throw an exception instead
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

    if(!validateRoundBrackets(currentLine[VALUE_COLUMN])) {
        errors[rowCount] = ErrorCode.ROUND_BRACKET_ERROR.code
    }

    if(!validateComparators(currentLine[VALUE_COLUMN])) {
        errors[rowCount] = ErrorCode.INCORRECT_COMPARATOR_ERROR.code
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
        val segLoop = currentLine[LOOP_COLUMN].replace("\r", "")
        val loopCode = if(segLoop.toIntOrNull() != null) {
            "\tfor (int loopCount = 0; loopCount < ${segLoop.toInt()}; loopCount++) {\n"
        } else if(segLoop.isNotBlank()) {
            "\tint ${currentLine[SEGMENT_COLUMN].lowercase()}Count = 0;\n" +
            generateLoops(segLoop.split(">")) +
            "\t\tif(${currentLine[SEGMENT_COLUMN].lowercase()}Count++ < ${currentLine[TYPE_COLUMN]}) {\n"
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
            endOfSegmentCode = if(loopCode.isNotBlank()) loopCode.split("{").dropLast(1).joinToString("") { "}" } else "")
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
            val conditional = createConditional(splitCond.subList(1, splitCond.size), if(currentLine[7] != "") { currentSegmentVariable } else { currentVariable } + ".set${createObjectType(currentLine[8], currentLine[10], standard)}${currentLine[9]}(" + createTypeParse(currentLine[12]), currentLine, standard, fields, fieldCloser = if(createTypeParse(currentLine[12]) != "") ")" else "")

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

        val subStringCode = if(currentLine[COMPONENT_COLUMN].isNotBlank()) {
            val componentLength = currentLine[TYPE_COLUMN].toIntOrNull() ?: getComponentLength(standard)
            val lowerBound = (Integer.valueOf(currentLine[COMPONENT_COLUMN]) - 1) * componentLength
            val upperBound = Integer.valueOf(currentLine[COMPONENT_COLUMN]) * componentLength
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