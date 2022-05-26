package com.locussoftware.arse.ae.entities

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table

@Table("SPECIFICATIONS")
data class Specification(@Id val id: String?, val specification_name: String)
