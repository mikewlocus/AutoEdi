package com.locussoftware.arse.ae.entities

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table

@Table("SPECIFICATION_ROWS")
data class SpecificationRow (
    @Id val id: String?,
    val specification_id: String,
    var seg_group: String?,
    var depth_5: String?,
    var depth_4: String?,
    var depth_3: String?,
    var depth_2: String?,
    var depth_1: String?,
    var segment: String?,
    var element: String?,
    var sub_element: String?,
    var component: String?,
    var field_name: String?,
    var arsecode: String?,
    var field_count: String?,
    var looping_logic: String?,
    var comments: String?,
    var error_code: Int?,
    var row_index: Int?
)
