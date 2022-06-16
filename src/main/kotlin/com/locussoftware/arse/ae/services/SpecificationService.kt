package com.locussoftware.arse.ae.services

import com.locussoftware.arse.ae.GeneratorResult
import com.locussoftware.arse.ae.entities.Specification
import com.locussoftware.arse.ae.repositories.SpecificationRepository
import generator
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

@Service
class SpecificationService (val db: SpecificationRepository) {

    fun findSpecifications() : List<Specification> = db.findSpecifications()

    fun findByIdOrNull(id: String) : Specification? = db.findByIdOrNull(id)

    fun post(specification: Specification) : Specification {
        return db.save(specification)
    }

    fun delete(specification: Specification) {
        db.delete(specification)
    }

    fun generate(csv: String, fileName: String, variables: String) : GeneratorResult {
        return generator(csv, fileName, variables)
    }

}