package com.locussoftware.arse.ae.services

import com.locussoftware.arse.ae.MassEditQuery
import com.locussoftware.arse.ae.entities.Specification
import com.locussoftware.arse.ae.entities.SpecificationRow
import org.springframework.stereotype.Service

private const val MESSAGE_TYPE_INDEX = 0
private const val VERSION_INDEX = 1

private const val SPECIFICATION_NAME_MIN_SIZE = 2

/**
 * Provides functionality to the mass editor screen, primarily involving executing queries.
 */
@Service
class MassEditorService (val specificationService: SpecificationService,
                         val specificationRowService: SpecificationRowService) {

    /**
     * Gets a list of specifications which match the given message type and version.
     *
     * Either one or both of message type and version will be ignored by passing them as "None". Passing both as "None"
     * will return all specifications in the database.
     *
     * @param messageType The message type of the specifications to be returned, or "None" if it doesn't matter.
     * @param version The version of the specifications to be returned, or "None" if it doesn't matter.
     * @return A list of specifications based on the limitations passed into the method parameters.
     */
    fun getSpecifications(messageType: String, version: String) : List<Specification> {
        // All parameters are "None", return all specifications
        if(messageType == "None" && version == "None") {
            return specificationService.findSpecifications()
        }

        val specList = mutableListOf<Specification>()

        // Loop through all specifications
        for(spec in specificationService.findSpecifications()) {
            val splitName = spec.specification_name.split("_")

            // Check that the specification type and version matches the parameters
            if(splitName.size >= SPECIFICATION_NAME_MIN_SIZE
                && (messageType == "None" || splitName[MESSAGE_TYPE_INDEX] == messageType)
                && (version == "None" || splitName[VERSION_INDEX] == version)) {
                specList.add(spec)
            }
        }

        return specList
    }

    /**
     * Loops through the rows of the relevant specifications, to find the rows which the query requires to be changed.
     *
     * @param specifications The list of specifications relevant to the query.
     * @param massEditQuery The query object, storing the user-entered values which need to be searched for.
     * @return A list of rows which need to be updated by the query.
     */
    fun getRelevantRows(specifications: List<Specification>, massEditQuery: MassEditQuery) : List<SpecificationRow> {
        val relevantRows = mutableListOf<SpecificationRow>()

        // Loop through all relevant specifications
        for(spec in specifications) {
            val specRows = specificationRowService.findSpecificationRows(spec.id!!)

            // Loop through all rows in specification
            for(specRow in specRows) {
                // Check the query criteria matches the row, or is blank (and therefore considered irrelevant)
                if((massEditQuery.segGroupIn.isBlank() || massEditQuery.segGroupIn == specRow.seg_group)
                    && (massEditQuery.segCodeIn.isBlank() || massEditQuery.segCodeIn == specRow.segment)
                    && (massEditQuery.elementCodeIn.isBlank() || massEditQuery.elementCodeIn == specRow.element)
                    && (massEditQuery.subElementCodeIn.isBlank() || massEditQuery.subElementCodeIn == specRow.sub_element)
                    && (massEditQuery.fieldNameIn.isBlank() || massEditQuery.fieldNameIn == specRow.field_name)
                    && (massEditQuery.componentNumberIn.isBlank() || massEditQuery.componentNumberIn == specRow.component)
                    && (massEditQuery.arseCodeIn.isBlank() || massEditQuery.arseCodeIn == specRow.arsecode)
                    && (massEditQuery.numberIn.isBlank() || massEditQuery.numberIn == specRow.field_count)
                    && (massEditQuery.loopingLogicIn.isBlank() || massEditQuery.loopingLogicIn == specRow.looping_logic)) {
                    relevantRows.add(specRow)
                }
            }
        }

        return relevantRows
    }

    /**
     * Performs a mass edit on the specifications and rows which are targeted by the query.
     *
     * Calls methods to identify the relevant rows from the relevant specifications, then updates the necessary values
     * in those rows.
     *
     * @param massEditQuery The query object, holding both the search criteria and the target values.
     */
    fun performMassEdit(massEditQuery: MassEditQuery) {
        val relevantSpecs = getSpecifications(massEditQuery.typeLim, massEditQuery.versionLim)
        val relevantRows = getRelevantRows(relevantSpecs, massEditQuery)

        // Loop through rows relevant to the query
        for(row in relevantRows) {
            // Update the segment group
            if(massEditQuery.segGroupIn.isNotBlank() || massEditQuery.segGroupOut.isNotBlank()) {
                row.seg_group = massEditQuery.segGroupOut
            }

            // Update the segment code
            if(massEditQuery.segCodeIn.isNotBlank() || massEditQuery.segCodeOut.isNotBlank()) {
                row.segment = massEditQuery.segCodeOut
            }

            // Update the element code
            if(massEditQuery.elementCodeIn.isNotBlank() || massEditQuery.elementCodeOut.isNotBlank()) {
                row.element = massEditQuery.elementCodeOut
            }

            // Update the sub-element code
            if(massEditQuery.subElementCodeIn.isNotBlank() || massEditQuery.subElementCodeOut.isNotBlank()) {
                row.sub_element = massEditQuery.subElementCodeOut
            }

            // Update the component number
            if(massEditQuery.componentNumberIn.isNotBlank() || massEditQuery.componentNumberOut.isNotBlank()) {
                row.component = massEditQuery.componentNumberOut
            }

            // Update the field name
            if(massEditQuery.fieldNameIn.isNotBlank() || massEditQuery.fieldNameOut.isNotBlank()) {
                row.field_name = massEditQuery.fieldNameOut
            }

            // Update the ArseCode
            if(massEditQuery.arseCodeIn.isNotBlank() || massEditQuery.arseCodeOut.isNotBlank()) {
                row.arsecode = massEditQuery.arseCodeOut
            }

            // Update the field count
            if(massEditQuery.numberIn.isNotBlank() || massEditQuery.numberOut.isNotBlank()) {
                row.field_count = massEditQuery.numberOut
            }

            // Update the looping logic
            if(massEditQuery.loopingLogicIn.isNotBlank() || massEditQuery.loopingLogicOut.isNotBlank()) {
                row.looping_logic = massEditQuery.loopingLogicOut
            }

            // Save the update
            specificationRowService.post(row)
        }
    }

}