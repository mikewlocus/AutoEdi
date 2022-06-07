package com.locussoftware.arse.ae.repositories

import com.locussoftware.arse.ae.entities.Change
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository

interface ChangeRepository: CrudRepository<Change, String> {

    @Query("SELECT * FROM CHANGES")
    fun findChanges(): List<Change>

}