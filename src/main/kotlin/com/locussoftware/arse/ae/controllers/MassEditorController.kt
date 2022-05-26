package com.locussoftware.arse.ae.controllers

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

@Controller
class MassEditorController {

    @GetMapping("/mass-editor")
    fun massEditor() : String {
        return "/mass-editor"
    }

}