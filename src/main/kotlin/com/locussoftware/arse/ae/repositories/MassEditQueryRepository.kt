package com.locussoftware.arse.ae.repositories

import com.locussoftware.arse.ae.entities.MassEditQuery
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository

interface MassEditQueryRepository: CrudRepository<MassEditQuery, String> {

    @Query("SELECT * FROM MASS_EDIT_QUERIES")
    fun findMassEditQueries(): List<MassEditQuery>

}