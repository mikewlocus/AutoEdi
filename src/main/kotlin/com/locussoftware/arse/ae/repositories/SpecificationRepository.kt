package com.locussoftware.arse.ae.repositories

import com.locussoftware.arse.ae.entities.Specification
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param

interface SpecificationRepository: CrudRepository<Specification, String> {

    @Query("SELECT * FROM SPECIFICATIONS")
    fun findSpecifications(): List<Specification>

    @Query("SELECT * FROM SPECIFICATIONS WHERE SPECIFICATIONS.specification_name = :spec_name")
    fun findSpecificationsByName(@Param("spec_name") specificationName: String): List<Specification>

}