package com.locussoftware.arse.ae

data class MassEditQuery(var id: String?,
                         var typeLim: String,
                         var versionLim: String,
                         var segGroupIn: String,
                         var segCodeIn: String,
                         var elementCodeIn: String,
                         var subElementCodeIn: String,
                         var componentNumberIn: String,
                         var fieldNameIn: String,
                         var arseCodeIn: String,
                         var numberIn: String,
                         var loopingLogicIn: String,
                         var segGroupOut: String,
                         var segCodeOut: String,
                         var elementCodeOut: String,
                         var subElementCodeOut: String,
                         var componentNumberOut: String,
                         var fieldNameOut: String,
                         var arseCodeOut: String,
                         var numberOut: String,
                         var loopingLogicOut: String)