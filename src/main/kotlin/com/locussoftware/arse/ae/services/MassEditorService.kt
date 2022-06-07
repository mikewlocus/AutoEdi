package com.locussoftware.arse.ae.services

import com.locussoftware.arse.ae.entities.MassEditQuery
import com.locussoftware.arse.ae.entities.Specification
import com.locussoftware.arse.ae.entities.SpecificationRow
import com.locussoftware.arse.ae.repositories.MassEditQueryRepository
import org.springframework.stereotype.Service

/**
 * Provides functionality to the mass editor screen, primarily involving executing queries.
 */
@Service
class MassEditorService (val specificationService: SpecificationService,
                         val specificationRowService: SpecificationRowService,
                         val massEditQueryRepository: MassEditQueryRepository,
                         val changeService: ChangeService) {

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

            // Check that the specification type and version matches the parameters
            if((messageType == "None" || spec.message_type == messageType)
                && (version == "None" || spec.version == version)) {
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

            var occurrenceCount = 1

            // Loop through all rows in specification
            for(specRow in specRows) {
                // Check the query criteria matches the row, or is blank (and therefore considered irrelevant)
                if((massEditQuery.seg_group_in.isBlank() || massEditQuery.seg_group_in == specRow.seg_group)
                    && (massEditQuery.segment_in.isBlank() || massEditQuery.segment_in == specRow.segment)
                    && (massEditQuery.element_in.isBlank() || massEditQuery.element_in == specRow.element)
                    && (massEditQuery.sub_element_in.isBlank() || massEditQuery.sub_element_in == specRow.sub_element)
                    && (massEditQuery.field_name_in.isBlank() || massEditQuery.field_name_in == specRow.field_name)
                    && (massEditQuery.component_in.isBlank() || massEditQuery.component_in == specRow.component)
                    && (massEditQuery.arsecode_in.isBlank() || massEditQuery.arsecode_in == specRow.arsecode)
                    && (massEditQuery.field_count_in.isBlank() || massEditQuery.field_count_in == specRow.field_count)
                    && (massEditQuery.looping_logic_in.isBlank() || massEditQuery.looping_logic_in == specRow.looping_logic)) {
                    // Add row to list of matches
                    if(massEditQuery.occurrence == 0 || occurrenceCount == massEditQuery.occurrence) {
                        relevantRows.add(specRow)
                    }

                    // Increase the occurrence count if relevant to query
                    if(massEditQuery.occurrence > 0) {
                        occurrenceCount++
                    }
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
        val relevantSpecs = getSpecifications(massEditQuery.type_lim, massEditQuery.version_lim)
        val relevantRows = getRelevantRows(relevantSpecs, massEditQuery)

        val savedQuery = massEditQueryRepository.save(massEditQuery)

        // Loop through rows relevant to the query
        for(row in relevantRows) {

            // Save the row before it is changed
            changeService.postChangeFromRow(savedQuery.id!!, row)

            // Update the segment group
            if(massEditQuery.seg_group_in.isNotBlank() || massEditQuery.seg_group_out.isNotBlank()) {
                row.seg_group = massEditQuery.seg_group_out
            }

            // Update the segment code
            if(massEditQuery.segment_in.isNotBlank() || massEditQuery.segment_out.isNotBlank()) {
                row.segment = massEditQuery.segment_out
            }

            // Update the element code
            if(massEditQuery.element_in.isNotBlank() || massEditQuery.element_out.isNotBlank()) {
                row.element = massEditQuery.element_out
            }

            // Update the sub-element code
            if(massEditQuery.sub_element_in.isNotBlank() || massEditQuery.sub_element_out.isNotBlank()) {
                row.sub_element = massEditQuery.sub_element_out
            }

            // Update the component number
            if(massEditQuery.component_in.isNotBlank() || massEditQuery.component_out.isNotBlank()) {
                row.component = massEditQuery.component_out
            }

            // Update the field name
            if(massEditQuery.field_name_in.isNotBlank() || massEditQuery.field_name_out.isNotBlank()) {
                row.field_name = massEditQuery.field_name_out
            }

            // Update the ArseCode
            if(massEditQuery.arsecode_in.isNotBlank() || massEditQuery.arsecode_out.isNotBlank()) {
                row.arsecode = massEditQuery.arsecode_out
            }

            // Update the field count
            if(massEditQuery.field_count_in.isNotBlank() || massEditQuery.field_count_out.isNotBlank()) {
                row.field_count = massEditQuery.field_count_out
            }

            // Update the looping logic
            if(massEditQuery.looping_logic_in.isNotBlank() || massEditQuery.looping_logic_out.isNotBlank()) {
                row.looping_logic = massEditQuery.looping_logic_out
            }

            // Save the update
            specificationRowService.post(row)
        }
    }

    fun getQueryHistory() : List<MassEditQuery> {
        return massEditQueryRepository.findMassEditQueries()
    }

}