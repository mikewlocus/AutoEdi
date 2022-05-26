/*
This file contains functions which act upon the generated body code once it has been generated
 */

/**
 * Wraps code outside of segment groups in its own "createXYZ()" method, based on an object code XYZ
 */
tailrec fun wrapLooseCode(code: List<String>, sheetLines: List<String>, fields: List<String>, wrappedCode: String = "", newLineCount: Int = 0, inSegmentGroup: Boolean = false, wrapObject: String = ""): String {

    // Base case, return on empty list
    if(code.isEmpty()) {
        return wrappedCode
    }

    if(code[0] == "") {
        return wrapLooseCode(code.subList(1, code.size), sheetLines, fields, wrappedCode + code[0] + if(wrapObject != "") {"\treturn ${wrapObject.toLowerCase()};${System.lineSeparator()}}${System.lineSeparator()}"} else {""} + System.lineSeparator(), newLineCount + 1, inSegmentGroup, "")
    }

    if(code[0].split(System.lineSeparator())[0].split(" ").size > 1) {
        val sectObject = code[0].split(System.lineSeparator())[0].split(" ")[1]

        // Already-wrapped segment group
        if(sectObject.contains("SegmentGroup")) {
            return wrapLooseCode(code.subList(1, code.size), sheetLines, fields, wrappedCode + code[0] + System.lineSeparator(), inSegmentGroup = true)
        } else if(code[0].split(System.lineSeparator())[0].contains("return sg")) {
            return wrapLooseCode(code.subList(1, code.size), sheetLines, fields, wrappedCode + code[0] + System.lineSeparator(), inSegmentGroup = false)
        }

        // Loose code found, time to wrap
        if(newLineCount == 1 && !inSegmentGroup) {
            val prevOcc = wrappedCode.split("create${sectObject}").size - 1

            return wrapLooseCode(code.subList(1, code.size), sheetLines, fields, wrappedCode + "protected ${code[1].split(" ", "\t")[1]} create${sectObject}" + if(prevOcc > 0) {"_${prevOcc}"} else {""} + "(${createParameters(sheetLines.subList(0, looseSegmentLength(sheetLines.subList(1, sheetLines.size))), fields).joinToString(", "){ it.split(" ")[2] }}) throws OdysseyException {${System.lineSeparator()}", wrapObject = sectObject)
        }
    }

    return wrapLooseCode(code.subList(1, code.size), sheetLines, fields, wrappedCode + code[0] + System.lineSeparator(), newLineCount = 0, inSegmentGroup, wrapObject)

}

/**
 * Creates class-visible segment counting variables. These cannot be an array, due to segment increments (e.g. segmentGroup4_3)
 */
tailrec fun segCounters(sheetLines: List<String>, standard: String, output: String = ""): String {
    if(sheetLines.isEmpty()) {
        return output
    }

    val currentLine = sheetLines[0].split(",")


    // Segment groups
    if(currentLine[0] != "") {
        if (currentLine[0] != "------" && plusColumn(currentLine) == 5) {
            val sgNum = currentLine[0].split("SG")[1]
            val increment = if (currentLine[12].toIntOrNull() != null && currentLine[12].toInt() > 1 && output.split("count${sgNum}").size > 1) {
                "_${output.split("count${sgNum}").size - 1}"
            } else {
                ""
            }

            return segCounters(sheetLines.subList(1, sheetLines.size), standard, output + "private int count${sgNum + increment} = 0;${System.lineSeparator()}")
        }
    }

    // Loose methods
    if(currentLine[5] == "" && currentLine[6] != "" && currentLine[11] != "DO NOT CREATE" && isNotUNSegment(currentLine[6])) {

        val increment = if (currentLine[12].toIntOrNull() != null && currentLine[12].toInt() > 1 && output.split("count${currentLine[6]}").size > 1) {
            "_${output.split("count${currentLine[6]}").size - 1}"
        } else {
            ""
        }

        return segCounters(sheetLines.subList(1, sheetLines.size), standard, output + "private int count${currentLine[6] + increment} = 0;${System.lineSeparator()}")
    }

    return segCounters(sheetLines.subList(1, sheetLines.size), standard, output)
}

/**
 * Creates a top-level function which calls the rest of the functions in the message class.
 */
tailrec fun generateCreatorFunction(messageObjectName: String, sheetLines: List<String>, fields: List<String>, standard: String, function: String = ""): String {

    if(sheetLines.isEmpty()) {
        return function + "${System.lineSeparator()}\treturn ${messageObjectName.toLowerCase()};${System.lineSeparator()}}${System.lineSeparator()}"
    }

    val currentLine = sheetLines[0].split(",")
    val segGroupLength = segmentGroupLength(sheetLines.subList(1, sheetLines.size), plusColumn(currentLine))

    val loopLogic = currentLine[13].replace("\r", "").split(">")
    val loopCode = if(loopLogic.size > 1) {
        generateLoops(loopLogic)
    } else {
        generateEntityVariables(loopLogic[0].split(";"))
    }
    val loopClosing = if(loopLogic.size > 1) {
        loopLogic.subList(1, loopLogic.size).joinToString("") { "\t}${System.lineSeparator()}" }
    } else if(loopCode.isNotEmpty()) {
        val splitOnIfs = loopCode.split("if(true)")
        splitOnIfs.subList(1, splitOnIfs.size).joinToString("") { "\t}${System.lineSeparator()}" }
    } else {
        ""
    }

    // Segment groups
    if(currentLine[0] != "") {
        if(currentLine[0] != "------" && plusColumn(currentLine) == 5) {
            val sgNum = currentLine[0].split("SG")[1]

            if(currentLine[12].toIntOrNull() != null && currentLine[12].toInt() > 1) {
                // List<SegmentGroupX> sgXs = new ArrayList<>();
                // messageObjectName.setSegmentGroupX(sgXs);
                // SGX sgX = createSGX()
                // if(SegmentCounter.getSegmentCount(sgX) > 0) {
                //  sgXs.add();
                // } else { count--; }
                val increment = if(function.split("createSegmentGroup${sgNum}").size > 1) { "_${function.split("createSegmentGroup${sgNum}").size - 1}" } else {""}
                val listCode = if(!function.contains("List<SegmentGroup${sgNum}>")) { "\tList<SegmentGroup${sgNum}> sg${sgNum}s = new ArrayList<>();${System.lineSeparator()}\t${messageObjectName.toLowerCase()}.setSegmentGroup${sgNum}(sg${sgNum}s);${System.lineSeparator()}" } else { "" }
                val segmentGroupVar = "\tSegmentGroup${sgNum} sg${sgNum + increment} = createSegmentGroup${sgNum}"  + increment + "(${(createParameters(sheetLines.subList(1, segGroupLength), fields) + listOf("  ++count${sgNum + increment}")).joinToString(", "){ it.split(" ")[2] }});"
                val noSegCode = "--count${sgNum + increment};"

                return generateCreatorFunction(messageObjectName,
                    sheetLines.subList(1, sheetLines.size),
                    fields,
                    standard,
                    function + listCode + loopCode + segmentGroupVar + "\n\tif(SegmentCounter.getSegmentCount(sg${sgNum + increment}) > 0) {\n\t\tsg${sgNum}s.add(sg${sgNum + increment});\n\t} else { $noSegCode }\n" + loopClosing)
            }

            // messageObjectName.setSegmentGroupX(createSegmentGroupX());
            return generateCreatorFunction(messageObjectName, sheetLines.subList(1, sheetLines.size), fields, standard, function + loopCode + "\t${messageObjectName.toLowerCase()}.setSegmentGroup${sgNum}(createSegmentGroup${sgNum}(${(createParameters(sheetLines.subList(1, segGroupLength), fields) + listOf("  ++count${sgNum}")).joinToString(", "){ it.split(" ")[2] }}));${System.lineSeparator()}" + loopClosing)
        }
    }

    // Loose methods
    if(currentLine[5] == "" && currentLine[6] != "" && currentLine[11] != "DO NOT CREATE" && isNotUNSegment(currentLine[6])) {
        val segmentName = createObjectType(currentLine[6], currentLine[10], standard)

        if(currentLine[12].toIntOrNull() != null && currentLine[12].toInt() > 1) {
            // List<XYZAbc> XYZs = new ArrayList<>();
            // messageObjectName.setXYZAbc(XYZs);
            // XYZs.add(createXYZ());
            val increment = if(function.split("create${currentLine[6]}").size > 1) { "_${function.split("create${currentLine[6]}").size - 1}" } else {""}
            return generateCreatorFunction(messageObjectName, sheetLines.subList(1, sheetLines.size), fields, standard, function + if(!function.contains("List<${segmentName}>")) { "\tList<${segmentName}> ${currentLine[6].toLowerCase()}s = new ArrayList<>();${System.lineSeparator()}\t${messageObjectName.toLowerCase()}.set${segmentName}(${currentLine[6].toLowerCase()}s);${System.lineSeparator()}" } else { "" } + loopCode + "\t${currentLine[6].toLowerCase()}s.add(create${currentLine[6]}" + if(function.split("create${currentLine[6]}").size > 1) { "_${function.split("create${currentLine[6]}").size - 1}" } else {""} + "(${(createParameters(sheetLines.subList(0, looseSegmentLength(sheetLines.subList(1, sheetLines.size))), fields) + listOf("  ++count${currentLine[6] + increment}")).joinToString(", "){ it.split(" ")[2] }}));${System.lineSeparator()}" + loopClosing)
        }
        // messageObjectName.setXYZAbc(createXYZ());
        return generateCreatorFunction(messageObjectName, sheetLines.subList(1, sheetLines.size), fields, standard, function + loopCode + "\t${messageObjectName.toLowerCase()}.set${segmentName}(create${currentLine[6]}(${(createParameters(sheetLines.subList(0, looseSegmentLength(sheetLines.subList(1, sheetLines.size))), fields) + listOf("  ++count${currentLine[6]}")).joinToString(", "){ it.split(" ")[2] }}));${System.lineSeparator()}" + loopClosing)
    }

    return generateCreatorFunction(messageObjectName, sheetLines.subList(1, sheetLines.size), fields, standard, function)
}

tailrec fun generateLoops(loopElements: List<String>, output: String = "") : String {
    if(loopElements.size <= 1) {
        return output
    }

    val currentLoop = loopElements[1].split(";")[0]
    val splitFirstElement = if(loopElements[0].isNotBlank()) {
        loopElements[0].replace("?", "").split(".")
    } else{
        // Blank first element; leave an empty list
        listOf()
    }

    val currentLoopVarName = currentLoop[0].toLowerCase() + currentLoop.substring(1)

    return if(splitFirstElement.isEmpty()) {
        val loopList = currentLoop.first().toLowerCase() + currentLoop.dropLast(1).drop(1) + if(currentLoop.last() == 'y') "ies" else currentLoop.last() + "s"
        val lastElementChecker = "\tthis.isLastEntity = $currentLoopVarName.equals($loopList.get($loopList.size() - 1));\n"

        generateLoops(loopElements.subList(1, loopElements.size), output + "\tfor($currentLoop $currentLoopVarName : $loopList) {${System.lineSeparator()}$lastElementChecker${generateLoopVariables(loopElements[1].split(";"))}")
    } else {
        // Access above levels within the loop

        val firstElementName = splitFirstElement[0][0].toLowerCase() + splitFirstElement[0].substring(1)
        val aboveLevelAccessors =  if(splitFirstElement.size > 1) { accessAboveLevel(splitFirstElement.subList(1, splitFirstElement.size), firstElementName) } else { firstElementName }

        val listAccessor = "$aboveLevelAccessors.get${currentLoop.dropLast(1)}${if (currentLoop.last() == 'y') "ies" else currentLoop.last() + "s"}()"
        val loopList = if(loopElements[0].isNotEmpty() && loopElements[0].last() == '?') {
            "($listAccessor.size() > 0) ? $listAccessor : Collections.singletonList(new $currentLoop())"
        } else {
            listAccessor
        }

        val lastElementChecker = "\tthis.isLastEntity = $currentLoopVarName.equals($loopList.get($loopList.size() - 1));\n"

        generateLoops(
            loopElements.subList(1, loopElements.size),
            output + "\tfor($currentLoop $currentLoopVarName : $loopList) {${System.lineSeparator()}$lastElementChecker${generateLoopVariables(loopElements[1].split(";"))}"
        )
    }
}

/**
 * No looping logic defined, just singular values that need to be accessed (usually going up a level, e.g. BookLineItem.Booking). Surrounds each with if(true), for easy local encapsulation.
 */
tailrec fun generateEntityVariables(loopLogic: List<String>, output: String = "") : String {
    if(loopLogic.isEmpty() || loopLogic[0].isBlank()) {
        return output
    }

    val splitFirstElement = loopLogic[0].split(".")
    val elementVar = splitFirstElement.last() + " " + splitFirstElement.last()[0].toLowerCase() + splitFirstElement.last().substring(1) + " = "

    val firstElementName = splitFirstElement[0][0].toLowerCase() + splitFirstElement[0].substring(1)

    return generateEntityVariables(loopLogic.subList(1, loopLogic.size), output + "\tif(true) {${System.lineSeparator()}\t" + if(splitFirstElement.size > 1) { elementVar + accessAboveLevel(splitFirstElement.subList(1, splitFirstElement.size), firstElementName) } else { "" } + ";${System.lineSeparator()}")
}

/**
 * Allows looping code to access higher elements through the '.' syntax,
 * e.g. "BookLineItem.Booking>BookingItinerary -> bookLineItem.getBooking().getBookingItineraries()"
 *
 * splitVar: A list of the first loop element, split on '.', ignoring the first element of the split (sublist(1, length))
 */
tailrec fun accessAboveLevel(splitVar: List<String>, output: String = "") : String {
    if(splitVar.isEmpty()) {
        return output
    }

    val newOutput = "(($output != null && $output.get${splitVar[0]}() != null) ? $output.get${splitVar[0]}() : new ${splitVar[0]}())"

    return accessAboveLevel(splitVar.subList(1, splitVar.size), newOutput)
}

tailrec fun generateLoopVariables(variables: List<String>, output: String = "") : String {
    if(variables.size <= 1) {
        return output
    }

    return if(variables[1].split(".").size > 1) {
        // Variable split on '.'
        val splitVar = variables[1].split(".")
        val baseVar = variables[0][0].toLowerCase() + variables[0].substring(1)
        val accessVar = joinVarCallWithNullChecks(splitVar, baseVar)

        generateLoopVariables(listOf(variables[0]) + variables.subList(2, variables.size), output + "\t\t${splitVar[splitVar.lastIndex]} ${splitVar[splitVar.lastIndex][0].toLowerCase() + splitVar[splitVar.lastIndex].substring(1)} = $accessVar;${System.lineSeparator()}")
    } else {
        generateLoopVariables(listOf(variables[0]) + variables.subList(2, variables.size), output + "\t\t${variables[1]} ${variables[1][0].toLowerCase() + variables[1].substring(1)} = ${variables[0][0].toLowerCase() + variables[0].substring(1)}.get${variables[1]}();${System.lineSeparator()}")
    }
}

/**
 * Joins the variable call split on '.' from the looping logic, adds ternary null checks along with getter calls.
 *
 * @param splitVar The variable call split on '.'.
 * @param output The tail recursive output storage parameter. This must be initially defined to the base variable.
 */
tailrec fun joinVarCallWithNullChecks(splitVar: List<String>, output: String) : String {
    if(splitVar.isEmpty()) {
        return output
    }

    return joinVarCallWithNullChecks(splitVar.subList(1, splitVar.size),
            "(($output != null) ? $output.get${splitVar[0]}() : null)")
}

fun generateImports(sheetLines: List<String>, messageType: String, versionCode: String) : String {
    return """
import java.io.Serializable;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.CheckForNull;
import javax.faces.application.FacesMessage;
import javax.persistence.EntityManager;

import org.apache.commons.lang3.StringUtils;
import org.jboss.seam.annotations.In;
import org.jboss.seam.annotations.Logger;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.annotations.Transactional;
import org.jboss.seam.faces.FacesMessages;
import org.jboss.seam.international.StatusMessage;
import org.jboss.seam.international.StatusMessages;
import org.jboss.seam.log.Log;

import org.milyn.smooks.edi.EDIWritable;
import org.milyn.smooks.edi.unedifact.model.r41.UNEdifactInterchange41;
import org.milyn.smooks.edi.unedifact.model.r41.UNEdifactMessage41;
import org.milyn.smooks.edi.unedifact.model.r41.UNB41;
import org.milyn.smooks.edi.unedifact.model.r41.UNH41;
import org.milyn.smooks.edi.unedifact.model.r41.UNT41;
import org.milyn.smooks.edi.unedifact.model.r41.UNZ41;
import org.milyn.smooks.edi.unedifact.model.r41.types.DateTime;
import org.milyn.smooks.edi.unedifact.model.r41.types.MessageIdentifier;
import org.milyn.smooks.edi.unedifact.model.r41.types.SyntaxIdentifier;

import com.locuslive.odyssey.edi.*;
import com.locuslive.odyssey.entity.*;
import com.locuslive.odyssey.batch.BatchResponse;
import com.locuslive.odyssey.constant.ContainerCharacteristic;
import com.locuslive.odyssey.constant.GenericStatus;
import com.locuslive.odyssey.constant.PartyType;
import com.locuslive.odyssey.constant.ServiceType;
import com.locuslive.odyssey.constant.Strings;
import com.locuslive.odyssey.constant.Charset;
import com.locuslive.odyssey.constant.ContainerEventType;
import com.locuslive.odyssey.edi.IntegrationMessageType;
import com.locuslive.odyssey.edi.MessageFunction;
import com.locuslive.odyssey.edi.SmooksController;
import com.locuslive.odyssey.edi.booking.BookingInterface;
import com.locuslive.odyssey.edi.booking.ExportedBookingCheck;
import com.locuslive.odyssey.edi.booking.MessagePlatform;
import com.locuslive.odyssey.edi.coparn.CoparnType;
import com.locuslive.odyssey.edi.coprar.CoprarType;
import com.locuslive.odyssey.edi.coprar.AbstractCoprarHandler;
import com.locuslive.odyssey.edi.coreor.IProcessCoreor;
import com.locuslive.odyssey.edi.util.COMType;
import com.locuslive.odyssey.edi.util.ContactInformationType;
import com.locuslive.odyssey.edi.util.ContractCarriageCode;
import com.locuslive.odyssey.edi.util.DateTimeFormat;
import com.locuslive.odyssey.edi.util.DateTimePeriodQualifier;
import com.locuslive.odyssey.edi.util.FreeTextQualifier;
import com.locuslive.odyssey.edi.util.LocationFunctionCode;
import com.locuslive.odyssey.edi.util.LocationType;
import com.locuslive.odyssey.edi.util.MeasuredAttributeCode;
import com.locuslive.odyssey.edi.util.MeasurementApplicationQualifier;
import com.locuslive.odyssey.edi.util.MessageTypeCode;
import com.locuslive.odyssey.edi.util.NadParty;
import com.locuslive.odyssey.edi.util.ReferenceQualifier;
import com.locuslive.odyssey.edi.util.SegmentCounter;
import com.locuslive.odyssey.edi.util.TemperatureTypeCode;
import com.locuslive.odyssey.edi.util.TransportIdentification;
import com.locuslive.odyssey.edi.util.TransportMeans;
import com.locuslive.odyssey.edi.util.TransportMode;
import com.locuslive.odyssey.edi.util.TransportStage;
import com.locuslive.odyssey.edi.util.d06a.EdiD06AFactory;
import com.locuslive.odyssey.edi.coparn.IProcessCoparn;
import com.locuslive.odyssey.edi.ifcsum.AbstractIfcsumHandler;
import com.locuslive.odyssey.edi.ifcsum.AbstractManifestGenerator;
import com.locuslive.odyssey.edi.iftsta.IftstaProcessor;
import com.locuslive.odyssey.edi.iftsta.AlternateIftstaType;
import com.locuslive.odyssey.edi.coparn.CoparnInfo;
import com.locuslive.odyssey.edi.cuscar.ProcessCuscar;
import com.locuslive.odyssey.session.container.reference.ContainerReleaseReferenceEdiInfo;
import com.locuslive.odyssey.util.CollectionUtil;
import com.locuslive.odyssey.entity.virtual.ContainerLocation;
import com.locuslive.odyssey.entity.virtual.Idable;
import com.locuslive.odyssey.entity.AddressBook;
import com.locuslive.odyssey.entity.BookLineItem;
import com.locuslive.odyssey.entity.Booking;
import com.locuslive.odyssey.entity.BookingItinerary;
import com.locuslive.odyssey.entity.BookingVoyage;
import com.locuslive.odyssey.entity.CommodityBookLineItemList;
import com.locuslive.odyssey.entity.Company;
import com.locuslive.odyssey.entity.CompanyAssociation;
import com.locuslive.odyssey.entity.ContType;
import com.locuslive.odyssey.entity.ContactDetail;
import com.locuslive.odyssey.entity.ContainerReleaseReference;
import com.locuslive.odyssey.entity.EdiRecipientConnectionDetail;
import com.locuslive.odyssey.entity.IntegrationMessageLog;
import com.locuslive.odyssey.entity.IntegrationMessageLog.IntegrationChildMessageType;
import com.locuslive.odyssey.entity.IntegrationMessageLog.IntegrationMessageDirection;
import com.locuslive.odyssey.entity.IntegrationMessageLog.IntegrationMessageStatus;
import com.locuslive.odyssey.entity.IntegrationMessageStore;
import com.locuslive.odyssey.entity.LineAmount;
import com.locuslive.odyssey.entity.Party;
import com.locuslive.odyssey.entity.PortOperationsReport;
import com.locuslive.odyssey.entity.SchedulePort;
import com.locuslive.odyssey.entity.ShipInstrLineItem;
import com.locuslive.odyssey.entity.ShippingInstruction;
import com.locuslive.odyssey.entity.SiContainerAllocation;
import com.locuslive.odyssey.entity.SiContainerCommodityList;
import com.locuslive.odyssey.entity.Terminal;
import com.locuslive.odyssey.entity.UnLocode;
import com.locuslive.odyssey.entity.User;
import com.locuslive.odyssey.entity.Vessel;
import com.locuslive.odyssey.entity.Voyage;
import com.locuslive.odyssey.entity.edi.BookingEdiMap;
import com.locuslive.odyssey.entity.edi.ExternalBooking;
import com.locuslive.odyssey.entity.edi.SchedulePortEdiMap;
import com.locuslive.odyssey.entity.account.Account;
import com.locuslive.odyssey.exception.OdysseyBatchException;
import com.locuslive.odyssey.exception.OdysseyException;
import com.locuslive.odyssey.international.MessagesHelper;
import com.locuslive.odyssey.performance.MeasureCalls;
import com.locuslive.odyssey.session.association.AgentAction;
import com.locuslive.odyssey.session.booking.VoyageLegMetaData;
import com.locuslive.odyssey.session.container.ContainerStockHelper;
import com.locuslive.odyssey.session.data.CustomDataMappingDAO;
import com.locuslive.odyssey.session.data.edi.iftmcs.GenerateIftmcs.MessageDirection;
import com.locuslive.odyssey.session.data.edi.iftmcs.GenerateIftmcs;
import com.locuslive.odyssey.session.errors.Error;
import com.locuslive.odyssey.session.integration.IntegrationMessageLogDAO;
import com.locuslive.odyssey.session.integration.MessageLogHelper;
import com.locuslive.odyssey.session.mapping.CustomDataMappingAction;
import com.locuslive.odyssey.session.ports.UnLocodeLookup;
import com.locuslive.odyssey.session.pricing.ChargeLocation;
import com.locuslive.odyssey.session.user.UserProfile;
import com.locuslive.odyssey.session.economiczone.EconomicZoneAction;
import com.locuslive.odyssey.hibernate.DirtyObject;

import org.milyn.edi.unedifact.${versionCode.toLowerCase()}.${messageType.toUpperCase()}.${messageType};
import org.milyn.edi.unedifact.${versionCode.toLowerCase()}.${versionCode.toUpperCase()}InterchangeFactory;

${generateImportsByLine(sheetLines, messageType, versionCode).toSet().toList().joinToString(System.lineSeparator()) }"""
}

tailrec fun generateImportsByLine(sheetLines: List<String>, messageType: String, versionCode: String, imports: List<String> = listOf()) : List<String> {
    if(sheetLines.isEmpty()) {
        return imports
    }

    val currentLine = sheetLines[0].split(",")

    return if(currentLine[0] != "" && currentLine[0] != "------" && !imports.contains(currentLine[0].split("SG")[1] + ";")) {
        generateImportsByLine(sheetLines.subList(1, sheetLines.size), messageType, versionCode, imports + listOf("import org.milyn.edi.unedifact.${versionCode.toLowerCase()}.${messageType.toUpperCase()}.SegmentGroup${currentLine[0].split("SG")[1]};"))
    } else if(currentLine[6] != "" && currentLine[10] != "" && currentLine[11] != "DO NOT CREATE" && !currentLine[6].contains("SEGMENT") && isNotUNSegment(currentLine[6])) {
        generateImportsByLine(sheetLines.subList(1, sheetLines.size), messageType, versionCode, imports + listOf("import org.milyn.edi.unedifact.${versionCode.toLowerCase()}.common.${toCamelCase(currentLine[10].split("/", " "))};"))
    } else if(currentLine[7] != "" && currentLine[10] != "" && currentLine[7][0] == 'C' && !checkSectionIsEmpty(sheetLines.subList(1, sheetLines.size))) {
        generateImportsByLine(sheetLines.subList(1, sheetLines.size), messageType, versionCode, imports + listOf("import org.milyn.edi.unedifact.${versionCode.toLowerCase()}.common.field.${toCamelCase(currentLine[10].split("/", " "))}${currentLine[7]};"))
    } else{
        generateImportsByLine(sheetLines.subList(1, sheetLines.size), messageType, versionCode, imports)
    }
}