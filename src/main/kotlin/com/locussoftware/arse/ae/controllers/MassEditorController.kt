package com.locussoftware.arse.ae.controllers

import com.locussoftware.arse.ae.EdiConstants
import com.locussoftware.arse.ae.entities.MassEditQuery
import com.locussoftware.arse.ae.services.ChangeService
import com.locussoftware.arse.ae.services.MassEditorService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.ui.set
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping

@Controller
class MassEditorController(val massEditorService: MassEditorService, val changeService: ChangeService) {

    /**
     * Initialises the model objects for display on the mass editor UI
     */
    @GetMapping("/mass-editor")
    fun massEditor(model: Model) : String {
        // Initialise the type limitations
        model["typeLimitations"] = listOf("None") + EdiConstants.messageTypes
        // Initialise the version limitations
        model["versionLimitations"] = listOf("None") + EdiConstants.versions
        // Default query object for storing query values
        model["massEditQuery"] = MassEditQuery(null,
            "None",
            "None",
            0,
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
        // Initialise query history from repository
        model["massEditQueries"] = massEditorService.getQueryHistory().reversed()

        return "/mass-editor"
    }

    /**
     * Handles query submissions from the UI, by triggering a mass edit query in the service layer.
     *
     * @param massEditQuery The model attribute containing the query data entered and submitted by the user.
     */
    @PostMapping("/mass-editor/query")
    fun massEditorQuery(@ModelAttribute massEditQuery: MassEditQuery) : String {
        massEditorService.performMassEdit(massEditQuery)

        return "redirect:/mass-editor"
    }

    /**
     * Restores the values changed by the query to those which were present before the query took place.
     *
     * @param id The id of the query to be reverted.
     */
    @PostMapping("/mass-editor/reverse/{id}")
    fun undoQuery(@PathVariable id: String) : String {
        changeService.undoChanges(id)

        return "redirect:/mass-editor"
    }

}