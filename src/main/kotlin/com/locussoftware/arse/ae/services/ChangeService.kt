package com.locussoftware.arse.ae.services

import com.locussoftware.arse.ae.entities.Change
import com.locussoftware.arse.ae.entities.SpecificationRow
import com.locussoftware.arse.ae.repositories.ChangeRepository
import org.springframework.stereotype.Service

@Service
class ChangeService (val db: ChangeRepository, val rowService: SpecificationRowService) {

    fun findChanges() : List<Change> = db.findChanges()

    fun findChangesForQuery(queryId: String) : List<Change> = db.findChangesForQuery(queryId)

    fun post(change: Change) {
        db.save(change)
    }

    /**
     * Creates a change entity, populated with the given query id and specification row (before it's changed).
     *
     * @param queryId The ID of the query making the changes.
     * @param spec_row The specification row object before it's changed, to have values copied from.
     */
    fun postChangeFromRow(queryId: String, spec_row: SpecificationRow) {
        db.save(Change(null,
            queryId,
            spec_row.id!!,
            spec_row.seg_group!!,
            spec_row.segment!!,
            spec_row.element!!,
            spec_row.sub_element!!,
            spec_row.component!!,
            spec_row.field_name!!,
            spec_row.arsecode!!,
            spec_row.field_count!!,
            spec_row.looping_logic!!))
    }

    /**
     * Loops through the rows changed by a query, and sets their values to the values prior to the query's execution.
     *
     * @param queryId The ID of the query to be reverted.
     */
    fun undoChanges(queryId: String) {
        val changes = findChangesForQuery(queryId)

        changes.forEach {
            // Find the row to be reversed
            val row = rowService.findRowById(it.spec_row_id)

            // Restore values
            row.seg_group = it.prev_seg_group
            row.segment = it.prev_segment
            row.element = it.prev_element
            row.sub_element = it.prev_sub_element
            row.component = it.prev_component
            row.field_name = it.prev_field_name
            row.arsecode = it.prev_arsecode
            row.field_count = it.prev_field_count
            row.looping_logic = it.prev_looping_logic

            // Persist changes
            rowService.post(row)
        }
    }

}