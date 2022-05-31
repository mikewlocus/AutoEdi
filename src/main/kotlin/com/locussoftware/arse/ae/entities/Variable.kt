package com.locussoftware.arse.ae.entities

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table

@Table("VARIABLES")
data class Variable(
    @Id var id: String?,
    var description: String,
    var var_name: String,
    var code: String,
    var var_type: String,
    var null_check: String,
    var var_params: String,
    var required_params: String,
    var additional_checks: String
)
