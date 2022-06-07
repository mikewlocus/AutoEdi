package com.locussoftware.arse.ae.repositories

import com.locussoftware.arse.ae.entities.Change
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param

interface ChangeRepository: CrudRepository<Change, String> {

    @Query("SELECT * FROM CHANGES")
    fun findChanges(): List<Change>

    @Query("SELECT * FROM CHANGES WHERE CHANGES.query_id = :query_id")
    fun findChangesForQuery(@Param("query_id") query_id: String): List<Change>

}