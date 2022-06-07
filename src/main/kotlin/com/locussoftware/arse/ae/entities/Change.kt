package com.locussoftware.arse.ae.entities

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table

@Table("CHANGES")
data class Change(@Id val id: String?,
                  val query_id: String,
                  val spec_row_id: String,
                  val prev_seg_group: String,
                  val prev_segment: String,
                  val prev_element: String,
                  val prev_sub_element: String,
                  val prev_component: String,
                  val prev_field_name: String,
                  val prev_arsecode: String,
                  val prev_field_count: String,
                  val prev_looping_logic: String)