package com.locussoftware.arse.ae.entities

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table

@Table("MASS_EDIT_QUERIES")
data class MassEditQuery(@Id var id: String?,
                         var type_lim: String,
                         var version_lim: String,
                         var seg_group_in: String,
                         var segment_in: String,
                         var element_in: String,
                         var sub_element_in: String,
                         var component_in: String,
                         var field_name_in: String,
                         var arsecode_in: String,
                         var field_count_in: String,
                         var looping_logic_in: String,
                         var seg_group_out: String,
                         var segment_out: String,
                         var element_out: String,
                         var sub_element_out: String,
                         var component_out: String,
                         var field_name_out: String,
                         var arsecode_out: String,
                         var field_count_out: String,
                         var looping_logic_out: String)