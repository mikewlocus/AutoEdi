package com.locussoftware.arse.ae.controllers

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.ui.set
import org.springframework.web.bind.annotation.GetMapping

@Controller
class MassEditorController {

    /**
     * Initialises the model objects for display on the mass editor UI
     */
    @GetMapping("/mass-editor")
    fun massEditor(model: Model) : String {
        // Initialise the type limitations
        model["typeLimitations"] = listOf("COPARN", "COPRAR", "CUSCAR", "IFTMBF", "IFTMIN")

        return "/mass-editor"
    }

}