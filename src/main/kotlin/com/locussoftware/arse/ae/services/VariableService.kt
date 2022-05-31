package com.locussoftware.arse.ae.services

import com.locussoftware.arse.ae.entities.Variable
import com.locussoftware.arse.ae.repositories.VariableRepository
import org.springframework.stereotype.Service

@Service
class VariableService(val db: VariableRepository) {

    fun getVariables() : List<Variable> {
        return db.findVariables()
    }

}