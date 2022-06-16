package com.locussoftware.arse.ae

data class ErrorRowWrapper(val segmentGroup: String?,
                           val segment: String?,
                           val element: String?,
                           val subElement: String?,
                           val component: String?,
                           val errorText: String?,
                           val rowId: String?)
