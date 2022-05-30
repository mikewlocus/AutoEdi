package com.locussoftware.arse.ae.controllers

import com.locussoftware.arse.ae.MassEditQuery
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.ui.set
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PostMapping

@Controller
class MassEditorController {

    /**
     * Initialises the model objects for display on the mass editor UI
     */
    @GetMapping("/mass-editor")
    fun massEditor(model: Model) : String {
        // Initialise the type limitations
        model["typeLimitations"] = listOf("None", "COPARN", "COPRAR", "CUSCAR", "IFTMBF", "IFTMIN")
        // Initialise the version limitations
        model["versionLimitations"] = listOf("None", "D95A", "D95B", "D99A", "D99B", "D00B")
        // Default query object for storing query values
        model["massEditQuery"] = MassEditQuery("None",
            "None",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "")

        return "/mass-editor"
    }

    /**
     * Handles query submissions from the UI.
     *
     * @param massEditQuery The model attribute containing the query data entered and submitted by the user.
     */
    @PostMapping("/mass-editor/query")
    fun massEditorQuery(@ModelAttribute massEditQuery: MassEditQuery) : String {



        return "redirect:/mass-editor"
    }

}