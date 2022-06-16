package com.locussoftware.arse.ae.services

import com.locussoftware.arse.ae.entities.SpecificationRow
import com.locussoftware.arse.ae.repositories.SpecificationRowRepository
import org.springframework.stereotype.Service

@Service
class SpecificationRowService (val db: SpecificationRowRepository) {

    fun findSpecificationRows(specification_id: String) : ArrayList<SpecificationRow> = db.findSpecificationRows(specification_id)

    fun post(specificationRow: SpecificationRow) {
        db.save(specificationRow)
    }

    fun deleteAllRowsInSpecification(specification_id: String) {
        val rows = db.findSpecificationRows(specification_id)

        rows.forEach { db.delete(it) }
    }

    fun findRowById(rowId: String) : SpecificationRow {
        return db.findById(rowId).get()
    }

    fun duplicateRowsFromIdToNextSegment(specification_id: String, rowId: String) {
        val rows = ArrayList(db.findSpecificationRows(specification_id).sortedBy { it.row_index })
        val rowsToCopy = mutableListOf<SpecificationRow>()

        var found = false
        var indexToCopyInto = 0

        for(row in rows) {
            // Check if next segment has been reached
            if(found && row.segment != "") {
                indexToCopyInto = rows.indexOf(row)
                break
            }

            // Start copying from the selected row
            if(row.id == rowId) {
                found = true
            }

            // Perform copy
            if(found) {
                rowsToCopy.add(SpecificationRow(null, row.specification_id, row.seg_group, row.depth_5, row.depth_4, row.depth_3, row.depth_2, row.depth_1, row.segment, row.element, row.sub_element, row.component, row.field_name, row.arsecode, row.field_count, row.looping_logic, "", 0, 0))
            }
        }

        if(indexToCopyInto > 0) {
            rows.addAll(indexToCopyInto, rowsToCopy)
        }

        found = false;

        rows.forEach {
            // Update indicies
            if(it.row_index == 0 && rows.indexOf(it) != 0) {
                // Add correct index to new row
                it.row_index = rows[rows.indexOf(it) - 1].row_index
                found = true
            } else if(found) {
                // Increment existing indices
                it.row_index = it.row_index?.plus(rowsToCopy.size)
            }

            post(it)
        }
    }

    fun getAllRowsFromIdToNextElement() {

    }

    /**
     * Loops through the errors in the generator result, sets the error code on the relevant ordered rows.
     *
     * @param orderedRows The rows of a specification, in their correct order.
     * @param errors A hashmap containing the row number of the error, along with the error code as its value.
     */
    fun setErrorCodesFromErrorList(orderedRows: List<SpecificationRow>, errors: HashMap<Int, Int>) {
        errors.forEach {
            // Update error code
            orderedRows[it.key].error_code = it.value

            // Persist changed row
            this.post(orderedRows[it.key])
        }
    }

}