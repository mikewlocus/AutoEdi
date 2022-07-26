import com.locussoftware.arse.ae.ErrorCode
import com.locussoftware.arse.ae.GeneratorResult
import com.locussoftware.arse.ae.generator.validateComparators
import com.locussoftware.arse.ae.generator.validateRoundBrackets
import com.locussoftware.arse.ae.generator.validateSquareBrackets
import java.io.BufferedReader
import java.io.File
import java.util.logging.Logger

var errors: HashMap<Int, Int> = hashMapOf()
var rowCount: Int = 0

val LOG: Logger = Logger.getLogger("Generator")

/**
 * The entry point for the EDI message class generator.
 *
 * Determines the message parameters, before handing the message off for preprocessing and generation, then applying
 * postprocessing, and outputting the result.
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

    val fileNameSplitUnderscores = fileName.split("_")

    // Validate file name is Name_Standard_OptionalIdentifier, e.g. 'COPARN_D95B.csv', or 'COPARN_D95B_Locus.csv'
    val minimumUnderscoreSplits = 2

    if(fileNameSplitUnderscores.size < minimumUnderscoreSplits) {
        error ("Improper values passed to generator. File name must be provided in the format messageType_version_optionalName.csv")
    }

    val messageTypeIndex = 0
    val versionIndex = 1
    val nameIndex = 2

    val messageType = fileNameSplitUnderscores[messageTypeIndex].uppercase() +
            fileNameSplitUnderscores[messageTypeIndex].substring(1).lowercase()
    val version = fileNameSplitUnderscores[versionIndex].uppercase().split(".")[0]
    // Set the message standard based on the version; this is to handle Smooks' different naming conventions
    val standard = when (version) {
        "D95B" -> "D95B"
        "D16A" -> "D16A"
        else -> "D00B"
    }
    // The "name" of the specification, often the company who the message class is used to communicate with.
    val identifier = if (fileNameSplitUnderscores.size > nameIndex)
        fileNameSplitUnderscores[nameIndex].split(".").first()
    else
        ""

    // Split and clean file content
    val sheetLines = preprocess(csv.split(System.lineSeparator()))
    // Sort out speech marks in fields CSV
    val fields = preprocessFields(fieldsCsv)
    // Build file from generated code
    val code = generateEdiCode(sheetLines, fields, standard)

    // ---
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
    // ---

    // ---
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
    // ---

    // Postprocess the code, to remove any generation inefficiencies
    val postProcessed = postProcessGeneratedCode(generated)

    // Save the output file
    val outputFilePath = saveOutputToFile(messageType, version, identifier, postProcessed)

    return GeneratorResult(outputFilePath, errors)
}

/**
 * Preprocesses the fields CSV string, dealing with speech marks, then splitting it by line.
 *
 * @param fieldsCsv The CSV string containing the fields.
 * @return A list of strings, each string containing a single field.
 */
fun preprocessFields(fieldsCsv: String) : List<String> {
    val quoteString = "&dQuot1653061221"

    return fieldsCsv
        .replace("\"\"", quoteString)
        .replace("\"", "")
        .replace(quoteString, "\"")
        .split(System.lineSeparator())
}

/**
 * Post-processes the generated code, by carrying out replacements directly on the code.
 *
 * Currently starts off by replacing some expected output code with the naming abnormalities which Smooks requires,
 * followed by splitting the code on conditional logics. Next, it joins together any Strings with comparators, replacing
 * regular comparators with the Java-standard for string comparison. Lastly, a similar action is carried out on blank
 * strings, replacing comparisons with blank/not blank checks.
 *
 * @param code The generated code, fresh from the generator.
 * @return The post-processed code, ready for saving.
 */
fun postProcessGeneratedCode(code: String) : String {

    // Handle any replacements required by unconventional Smooks standards
    val cleanedCode =
        code
            .replace("(undg)", "UNDG") // Handle unconventional UNDG name
            .replace("UndgNumber", "UNDGNumber") // Handle non-capitalised UNDG number
            .replace("setEmsNumber", "setEMSNumber") // Handle non-capitalised EMS number
            .replace("  ", " ") // Get rid of double spaces

    // Split apart the code on the given regex, retaining the splits
    val conditionalPart = "}|== \"\"|==\"\"|!= \"\"|!=\"\"|== \"|==\"|!= \"|!=\"|&&|\\|\\||&& \\(|&&\\(|\\|\\| \\(|if\\(|if \\(|\\) \\{".toRegex()
    val conditionalSplitRegex = "(?<=($conditionalPart)|(?=($conditionalPart)".toRegex()

    val codeSplitOnConditionalParts = cleanedCode.split(conditionalSplitRegex)

    // Join together any Strings preceded by '==' or '!=' with proper Java-standard String comparisons
    val splitCodeJoinedOnStringEquals = joinStringsWithEquals(codeSplitOnConditionalParts)

    // Handle replacement of string comparators
    val postProcessed = splitCodeJoinedOnStringEquals.zipWithNext {
            a, b ->
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

    return postProcessed
}

/**
 * Saves the generated code to a file, creating a new file if it doesn't already exist.
 *
 * @param messageType The message type of the generated code, for naming the output file.
 * @param version The EDIFACT version of the generated code, for naming the output file.
 * @param identifier The identifier of the generated code, for naming the output file.
 * @param code The generated code to be saved in the output file.
 * @return The name of the output file.
 */
fun saveOutputToFile(messageType: String, version: String, identifier: String, code: String) : String {
    val outputFilePath = "Process${messageType}${version}$identifier.java"
    val file = File(outputFilePath)

    // Create new file if it doesn't already exist
    if(!file.exists()) {
        file.createNewFile()
    }

    // Write output to file
    File(outputFilePath).printWriter().use { out ->
        out.println(code)
    }

    return outputFilePath
}

/**
 * The main generator function for the message class.
 *
 * This function analyses the input CSV line-by-line, in order to produce the body of the message class.
 *
 * @param sheetLines The schema CSV, split by line.
 * @param fields The variables table CSV, split by line.
 * @param standard The Smooks standard to use for code output.
 * @return A string containing the generated Java code.
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
        val endOfFunctionCode = if (currentSegmentVariable != "" && currentGroup != "0")
            "\t}${System.lineSeparator()}"
        else ""
        val endOfFunctionReturn = if (currentGroup != "0")
            "\treturn sg${currentGroup.split("_").first()};${System.lineSeparator()}}"
        else ""

        return currentCode + endOfFunctionCode + System.lineSeparator() + endOfFunctionReturn + System.lineSeparator() + currentFunctions
    }

    LOG.info("Processing line: ${sheetLines.first()}")

    // Get an array of the cells in the current row
    val currentLine = sheetLines.first().split(",")

    // Set the row counter to the current row's index
    rowCount = currentLine[INDEX_COLUMN].toInt()

    // Error message due to improper formatting of the CSV file (all rows should have at least 12 cells, if they don't, it's an indication of the presence of a carriage return in a cell)
    if(currentLine.size < LOOP_COLUMN) {
        error (currentCode + currentFunctions + "The CSV file contains a formatting error, encountered here. This can be caused by a comma within a cell.")
    }

    // Validate the current line
    val errorOutput = "Error generating output from this specification. Please see error messages in application for more information."

    if(!validateSquareBrackets(currentLine[VALUE_COLUMN])) {
        errors[rowCount] = ErrorCode.SQUARE_BRACKET_ERROR.code
        error (errorOutput)
    }

    if(!validateRoundBrackets(currentLine[VALUE_COLUMN])) {
        errors[rowCount] = ErrorCode.ROUND_BRACKET_ERROR.code
        error (errorOutput)
    }

    if(!validateComparators(currentLine[VALUE_COLUMN])) {
        errors[rowCount] = ErrorCode.INCORRECT_COMPARATOR_ERROR.code
        error (errorOutput)
    }

    // Segment group creation methods
    val minimumSgLength = 3
    if(currentLine[SEGMENT_GROUP_COLUMN] != "" && currentLine[SEGMENT_GROUP_COLUMN].length >= minimumSgLength) {
        // If the first cell contains a value that isn't "------", it indicates the beginning of a new segment group
        if(currentLine[SEGMENT_GROUP_COLUMN] != "------") {
            // Find information about the segment group
            val segGroupLength = segmentGroupLength(sheetLines.subList(1, sheetLines.size), plusColumn(currentLine))
            val sgNumberStartPosition = 2
            val segGroupIn = currentLine[SEGMENT_GROUP_COLUMN].substring(sgNumberStartPosition)

            // Add incrementing appendage to method name if it already exists (e.g. segmentGroup1_1 representing the second SG1)
            val segGroup = when {

                // Check the current segment group parameter to see if it already has an incrementing appendage
                currentGroup.split("_").size > 1 -> {
                    // Remove the SG to get the previous increment number (e.g. SG_1_1 -> 1_1)
                    val splitPreviousIncrement = currentGroup.split("_").subList(1, currentGroup.split("_").size)
                    val previousIncrementNumber = splitPreviousIncrement.joinToString("_")

                    val newIncrement = currentCode.split("createSegmentGroup${segGroupIn}").size

                    // Rebuild with an additional increment
                    "${segGroupIn}_${previousIncrementNumber}_$newIncrement"
                }

                // Check the current code for any previous instances of the current segment group method
                currentCode.split("createSegmentGroup${segGroupIn}").size > 1 -> {
                    val newIncrement = currentCode.split("createSegmentGroup${segGroupIn}").size - 1

                    "${segGroupIn}_$newIncrement"
                }

                else -> segGroupIn
            }

            // Get the segment group number, removed of any appendages (e.g. SG1_1_2 -> SG1)
            val sgNum = segGroup.split("_").first()

            val loopLogic = currentLine[LOOP_COLUMN].replace("\r", "").split(">")
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

            val newCode = if(currentLine[TYPE_COLUMN].toIntOrNull() != null && currentLine[TYPE_COLUMN].toInt() > 1) {
                /*
                    // SGXs
                    List<SegmentGroupX> sgXs = new ArrayList<>();
                    sgY.setSegmentGroupX(sgXs);
                    sgXs.add(createSegmentGroupX());
                */
                currentCode + (if(currentSegmentVariable != "" && currentGroup != "0") "\t}$endOfSegmentCode${System.lineSeparator()}" else "") + if(currentGroup.split("_")[0].toInt() > 0) if(!currentCode.contains("List<SegmentGroup${sgNum}>")) { "${System.lineSeparator()}\t// SG${sgNum}s${System.lineSeparator()}\tList<SegmentGroup${sgNum}> sg${sgNum}s = new ArrayList<>();${System.lineSeparator()}\tsg${currentGroup.split("_")[0]}.setSegmentGroup${sgNum}(sg${sgNum}s);${System.lineSeparator()}" } else { "" } + "\tint count${segGroup} = 0;${System.lineSeparator()}" + loopCode + "\tsg${sgNum}s.add(createSegmentGroup${segGroup}" + "(${(createParameters(sheetLines.subList(1, segGroupLength), fields) + listOf("  ++count${segGroup}")).joinToString(", "){ it.split(" ")[2] }}));${System.lineSeparator()}" + loopClosing else ""
            } else {
                /*
                    // SGX
                    SegmentGroupX sgX = createSegmentGroupX()
                 */
                currentCode + (if(currentSegmentVariable != "" && currentGroup != "0") "\t}$endOfSegmentCode${System.lineSeparator()}" else "") + if(currentGroup.split("_")[0].toInt() > 0) "${System.lineSeparator()}\t// SG${sgNum}${System.lineSeparator()}\tSegmentGroup${sgNum} sg${sgNum} = createSegmentGroup${segGroup}(${(createParameters(sheetLines.subList(1, segGroupLength), fields) + listOf("  1")).joinToString(", "){ it.split(" ")[2] }});${System.lineSeparator()}\tsg${currentGroup.split("_")[0]}.setSegmentGroup${sgNum}(sg${sgNum});${System.lineSeparator()}" + loopClosing else ""
            }

            // Pass the new segment group into a new recursive branch, in order to add the new function to the main body of the class
            /*  protected SegmentGroupX createSegmentGroupX() {
                    SegmentGroupX sgX = new SegmentGroupX();
                    ...generateEdiCode()...
             */
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
    if(currentLine[SEGMENT_COLUMN] != "" && currentLine[VALUE_COLUMN] != "DO NOT CREATE" && !currentLine[SEGMENT_COLUMN].contains("SEG")) {
        // Skip over UN segments
        if(!isNotUNSegment(currentLine[SEGMENT_COLUMN])) {
            return generateEdiCode(sheetLines.subList(nextNonBlankCell(sheetLines.subList(1, sheetLines.size), 6) + 1, sheetLines.size), fields, standard, currentCode, currentFunctions, currentVariable, currentSegmentVariable, currentGroup)
        }

        // Create creation conditional
        val conditional = if(currentGroup != "0") {
            if(currentLine[VALUE_COLUMN].isNotEmpty() && currentLine[VALUE_COLUMN].toCharArray()[0] == '[') {
                createConditionWithChecks(currentLine[VALUE_COLUMN].trim('[', ']').split("(?<=(&&|\\|\\|))|(?=(&&|\\|\\|))".toRegex()), fields)
            } else{
                "true"
            }
        } else{
            ""
        }

        // Create loose method definition
        val methodDef = if(currentGroup == "0") {
            "${System.lineSeparator()}protected ${createObjectType(currentLine[SEGMENT_COLUMN], currentLine[ELEMENT_COLUMN], standard)} create${currentLine[SEGMENT_COLUMN]}(${(createParameters(sheetLines.subList(1, looseSegmentLength(sheetLines.subList(1, sheetLines.size))), fields) + listOf("int count")).joinToString(", ")}) {"
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
        val newCode = if(currentLine[TYPE_COLUMN].toIntOrNull() != null && currentLine[TYPE_COLUMN].toInt() > 1 && currentGroup != "0") {
            // List<XYZAbc> xyzs = new ArrayList<>();
            // sgX.setXYZAbc(xyzs);
            // XYZAbc xyz = new XYZAbc();
            // xyzs.add(xyz);
            currentCode + (if(currentSegmentVariable != "" && currentGroup != "0") "\t}$endOfSegmentCode${System.lineSeparator()}" else "") + methodDef + "${System.lineSeparator()}\t// ${currentLine[SEGMENT_COLUMN]}${System.lineSeparator()}" + if(!currentCode.contains("List<${createObjectType(currentLine[SEGMENT_COLUMN], currentLine[ELEMENT_COLUMN], standard)}>")) { "\tList<${createObjectType(currentLine[SEGMENT_COLUMN], currentLine[ELEMENT_COLUMN], standard)}> ${currentLine[SEGMENT_COLUMN].toLowerCase()}s = new ArrayList<>();${System.lineSeparator()}\tsg${currentGroup.split("_")[0]}.set${createObjectType(currentLine[SEGMENT_COLUMN], currentLine[ELEMENT_COLUMN], standard)}(${currentLine[SEGMENT_COLUMN].toLowerCase()}s);${System.lineSeparator()}"} else {""} + loopCode + (if(conditional != "") "\tif(${conditional}) {${System.lineSeparator()}" else "") + "\t${createSegmentObject(currentLine[SEGMENT_COLUMN], currentLine[ELEMENT_COLUMN], standard)}${System.lineSeparator()}\t${currentLine[SEGMENT_COLUMN].toLowerCase()}s.add(${currentLine[SEGMENT_COLUMN].toLowerCase()}" + ");${System.lineSeparator()}"
        } else{
            // XYZAbc xyz = new XYZAbc();
            // sgX.setXYZAbc(xyz);
            currentCode + (if(currentSegmentVariable != "" && currentGroup != "0") "\t}$endOfSegmentCode${System.lineSeparator()}" else "") + methodDef + "${System.lineSeparator()}\t// ${currentLine[SEGMENT_COLUMN]}${System.lineSeparator()}" + loopCode + (if(conditional != "") "\tif(${conditional}) {${System.lineSeparator()}" else "") + "\t${createSegmentObject(currentLine[SEGMENT_COLUMN], currentLine[ELEMENT_COLUMN], standard)}${System.lineSeparator()}" + (if(currentGroup != "0") "\tsg${currentGroup.split("_")[0]}.set${createObjectType(currentLine[SEGMENT_COLUMN], currentLine[ELEMENT_COLUMN], standard)}(${currentLine[SEGMENT_COLUMN].toLowerCase()});${System.lineSeparator()}" else "")
        }

        return generateEdiCode(sheetLines.subList(1, sheetLines.size),
            fields,
            standard,
            newCode,
            currentFunctions,
            currentSegmentVariable = currentLine[SEGMENT_COLUMN].lowercase(),
            currentGroup = currentGroup,
            endOfSegmentCode = if(loopCode.isNotBlank()) loopCode.split("{").dropLast(1).joinToString("") { "}" } else "")
    }

    // End of loose method
    val endOfLooseMethod = if((currentLine[ELEMENT_ID_COLUMN] != "" || currentLine[ELEMENT_SUB_ID_COLUMN] != "") && looseSegmentLength(sheetLines.subList(1, sheetLines.size)) == 0 && currentGroup == "0") {
        "${System.lineSeparator()}\treturn $currentSegmentVariable;${System.lineSeparator()}}${System.lineSeparator()}"
    } else{
        ""
    }

    // Secondary segment creation
    if(currentLine[ELEMENT_ID_COLUMN] != "") {
        // Check whether the current secondary segment has a number
        if(currentLine[ELEMENT_ID_COLUMN].first() == 'C') {
            if(!checkSectionIsEmpty(sheetLines.subList(1, sheetLines.size)))
                return generateEdiCode(sheetLines.subList(1, sheetLines.size),
                    fields,
                    standard,
                    currentCode + "\t${createSecondarySegmentObject(currentLine[ELEMENT_ID_COLUMN], currentLine[ELEMENT_COLUMN], standard)}${System.lineSeparator()}\t${currentSegmentVariable}.set${createObjectType(currentLine[ELEMENT_ID_COLUMN], currentLine[ELEMENT_COLUMN], standard)}${currentLine[COMPONENT_COLUMN]}(${currentLine[ELEMENT_ID_COLUMN].toLowerCase()});${System.lineSeparator()}" + endOfLooseMethod,
                    currentFunctions,
                    currentLine[ELEMENT_ID_COLUMN].toLowerCase(),
                    currentSegmentVariable,
                    currentGroup,
                    endOfSegmentCode)
        }
    }

    // Tertiary segment population
    if(((currentLine[ELEMENT_ID_COLUMN] != "" && currentLine[ELEMENT_ID_COLUMN].first() != 'C') || currentLine[ELEMENT_SUB_ID_COLUMN] != "") && currentLine[VALUE_COLUMN] != "---") {

        // Check for conditional syntax
        if(currentLine[VALUE_COLUMN].isNotEmpty() && currentLine[VALUE_COLUMN].toCharArray().first() == '[') {
            val splitCond = currentLine[VALUE_COLUMN].split("[", "]")
            val conditional = createConditional(splitCond.subList(1, splitCond.size), if(currentLine[ELEMENT_ID_COLUMN] != "") { currentSegmentVariable } else { currentVariable } + ".set${createObjectType(currentLine[ELEMENT_SUB_ID_COLUMN], currentLine[ELEMENT_COLUMN], standard)}${currentLine[9]}(" + createTypeParse(currentLine[TYPE_COLUMN]), currentLine, standard, fields, fieldCloser = if(createTypeParse(currentLine[TYPE_COLUMN]) != "") ")" else "")

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

        val field = getFields(currentLine[VALUE_COLUMN], fields)
        val locals = createLocals(currentLine[VALUE_COLUMN].split(";", "{", "}"), fields)
        val nullCheck = createMultiNullCheck(currentLine[VALUE_COLUMN].split(";", "{", "}"), fields)

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
            currentCode + (if(locals != "") "\t$locals" else "") + if(nullCheck != "") {"\tif(${nullCheck}) {${System.lineSeparator()}\t"} else {""} + "\t" + if(currentLine[ELEMENT_ID_COLUMN] != "") { currentSegmentVariable } else { currentVariable } + ".set${createObjectType(currentLine[ELEMENT_SUB_ID_COLUMN], currentLine[ELEMENT_COLUMN], standard)}${currentLine[9]}(" + createTypeParse(currentLine[TYPE_COLUMN]) + field + (if(createTypeParse(currentLine[TYPE_COLUMN]) != "") ")" else "") + subStringCode + ");${System.lineSeparator()}" + (if(nullCheck != "") {"\t}${System.lineSeparator()}"} else {""}) + endOfLooseMethod,
            currentFunctions,
            currentVariable,
            currentSegmentVariable,
            currentGroup,
            endOfSegmentCode)
    }

    // Skip irrelevant sections
    if(currentLine[VALUE_COLUMN] == "DO NOT CREATE") {
        return generateEdiCode(sheetLines.subList(looseSegmentLength(sheetLines.subList(1, sheetLines.size)) + 1, sheetLines.size), fields, standard, currentCode, currentFunctions, currentVariable, currentSegmentVariable, currentGroup, endOfSegmentCode)
    }

    // No relevant information
    return generateEdiCode(sheetLines.subList(1, sheetLines.size), fields, standard, currentCode + endOfLooseMethod, currentFunctions, currentVariable, currentSegmentVariable, currentGroup, endOfSegmentCode)
}