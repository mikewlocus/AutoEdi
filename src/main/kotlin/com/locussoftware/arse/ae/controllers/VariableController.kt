package com.locussoftware.arse.ae.controllers

import com.locussoftware.arse.ae.VariableRows
import com.locussoftware.arse.ae.entities.Variable
import com.locussoftware.arse.ae.services.VariableService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.ui.set
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PostMapping

@Controller
class VariableController(val variableService: VariableService) {

    @GetMapping("/variables")
    fun variables(model: Model) : String {
        model["variableRows"] = VariableRows(variableService.getVariables())

        return "/variables"
    }

    /**
     * Saves all of the variables.
     *
     * @param variables The variables from the form submission.
     */
    @PostMapping("/variables/save")
    fun saveVariables(@ModelAttribute variables: VariableRows) : String {
        variables.variables.forEach {
            variableService.db.save(it)
        }

        return "redirect:/variables"
    }

    /**
     * Adds a new variable.
     */
    @GetMapping("/variables/new")
    fun newVariable() : String {
        variableService.db.save(Variable(null,
            "",
            "\$untitled_variable",
            "",
            "",
            "",
            "",
            "",
            ""))

        return "redirect:/variables"
    }

}