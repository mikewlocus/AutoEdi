package com.locussoftware.arse.ae.repositories

import com.locussoftware.arse.ae.entities.SpecificationRow
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param

interface SpecificationRowRepository: CrudRepository<SpecificationRow, String> {

    @Query("SELECT * FROM SPECIFICATION_ROWS WHERE SPECIFICATION_ROWS.specification_id = :specification_id")
    fun findSpecificationRows(@Param("specification_id") specification_id: String): ArrayList<SpecificationRow>

}