package com.locussoftware.arse.ae.services

import com.locussoftware.arse.ae.entities.Specification
import com.locussoftware.arse.ae.repositories.SpecificationRepository
import generator
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

@Service
class SpecificationService (val db: SpecificationRepository) {

    fun findSpecifications() : List<Specification> = db.findSpecifications()

    fun findByIdOrNull(id: String) : Specification? = db.findByIdOrNull(id)

    fun findByNameOrNull(name: String) : Specification? {
        val specs = db.findSpecificationsByName(name)

        return if(specs.isNotEmpty()) {
            specs[0]
        } else {
            null
        }
    }

    fun post(specification: Specification) {
        db.save(specification)
    }

    fun delete(specification: Specification) {
        db.delete(specification)
    }

    fun generate(csv: String, fileName: String) : String {
        return generator(csv, fileName)
    }

}