package com.locussoftware.arse.ae.controllers

import com.locussoftware.arse.ae.VariableRows
import com.locussoftware.arse.ae.services.VariableService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.ui.set
import org.springframework.web.bind.annotation.GetMapping

@Controller
class VariableController(val variableService: VariableService) {

    @GetMapping("/variables")
    fun variables(model: Model) : String {
        model["variableRows"] = VariableRows(variableService.getVariables())

        return "/variables"
    }

}