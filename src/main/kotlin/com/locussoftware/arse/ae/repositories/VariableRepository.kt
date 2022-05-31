package com.locussoftware.arse.ae.repositories

import com.locussoftware.arse.ae.entities.Variable
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository

interface VariableRepository: CrudRepository<Variable, String> {

    @Query("SELECT * FROM VARIABLES")
    fun findVariables(): List<Variable>

}