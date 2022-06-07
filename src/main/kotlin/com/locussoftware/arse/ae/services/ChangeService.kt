package com.locussoftware.arse.ae.services

import com.locussoftware.arse.ae.entities.Change
import com.locussoftware.arse.ae.entities.SpecificationRow
import com.locussoftware.arse.ae.repositories.ChangeRepository
import org.springframework.stereotype.Service

@Service
class ChangeService (val db: ChangeRepository) {

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

}