package com.locussoftware.arse.ae.controllers

import com.locussoftware.arse.ae.*
import com.locussoftware.arse.ae.entities.Specification
import com.locussoftware.arse.ae.entities.SpecificationRow
import com.locussoftware.arse.ae.services.SpecificationRowService
import com.locussoftware.arse.ae.services.SpecificationService
import com.locussoftware.arse.ae.services.VariableService
import org.springframework.core.io.FileSystemResource
import org.springframework.http.MediaType
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.ui.set
import org.springframework.web.bind.WebDataBinder
import org.springframework.web.bind.annotation.*
import javax.servlet.http.HttpServletResponse

/**
 * Controller class for the specifications list and specification editor.
 *
 * @author Mike Wayne
 */
@Controller
class SpecificationController (val specificationService: SpecificationService,
                               val specificationRowService: SpecificationRowService,
                               val variableService: VariableService) {

    /**
     * Initialises the web data binder to allow for a higher number of parameters to be posted.
     */
    @InitBinder
    fun initBinder(binder: WebDataBinder) {
        binder.autoGrowCollectionLimit = 2000000000
    }

    @GetMapping
    fun index() : String {
        return "/index"
    }

    /**
     * Initialises the specifications list, along with model objects for creating new specifications.
     */
    @GetMapping("/specifications")
    fun specifications(model: Model) : String {
        // Find and display all specifications
        model["specifications"] = specificationService.findSpecifications()
        // Prepare blank specification object for new specifications
        model["specification"] = Specification(null, "", "", "")
        // Prepare specification object for import
        model["importSpec"] = ImportSpecification("", "", "", "")
        // Message types for new selection
        model["messageTypes"] = EdiConstants.messageTypes
        // Versions for new selection
        model["versions"] = EdiConstants.versions

        return "/specifications"
    }

    /**
     * Finds and displays the rows of a specification.
     *
     * @param id The ID of the specification to be displayed, passed through as a path variable.
     */
    @RequestMapping("/specifications/view/{id}")
    fun specificationRows(@PathVariable id: String, model: Model) : String {
        model["specificationRows"] = SpecificationRows(specificationRowService.findSpecificationRows(id).sortedBy { it.row_index })
        model["specificationId"] = id
        model["specification"] = specificationService.findByIdOrNull(id)!!
        return "/spec-editor"
    }

    /**
     * Deletes a specification entity, along with all of its rows.
     *
     * @param id The ID of the specification to be deleted, passed through as a path variable.
     */
    @RequestMapping("/specifications/delete/{id}")
    fun specificationRows(@PathVariable id: String) : String {
        // Find the specification using the ID
        val specToDelete = specificationService.findByIdOrNull(id)

        if(specToDelete != null) {
            // Delete all rows in the specification if found
            specificationRowService.deleteAllRowsInSpecification(id)
            // Delete the specification itself
            specificationService.delete(specToDelete)
        }

        return "redirect:/specifications"
    }

    @PostMapping("/specifications/post")
    fun postSpecification(@ModelAttribute specification: Specification) : String {
        specificationService.post(specification)
        return "redirect:/specifications"
    }

    /**
     * Creates a specification from a given CSV file.
     *
     * @param importSpec The model attribute containing the user-provided specification CSV and name to be imported.
     */
    @PostMapping("/specifications/import")
    fun importSpecification(@ModelAttribute importSpec: ImportSpecification) : String {

        // Create specification
        specificationService.post(
            Specification(null, importSpec.specification_name, importSpec.message_type, importSpec.version)
        )

        val spec = specificationService.findByNameOrNull(importSpec.specification_name)

        // Process CSV
        if(spec?.id != null) {
            getRowsFromCsv(preprocess(importSpec.csv.split("\n")), spec.id).forEach {
                specificationRowService.post(it)
            }
        }

        return "redirect:/specifications"
    }

    @PostMapping("/specifications/view/{id}/new-segment-group")
    fun newSegmentGroup(@PathVariable id: String) : String {
        specificationRowService.post(SpecificationRow(null, id, "SG1", "", "", "", "", "+", "+++", "", "", "", "", "", "", "", "", 0))
        return "redirect:/specifications/view/$id"
    }

    @PostMapping("/specifications/view/{id}/new-row")
    fun newRow(@PathVariable id: String) : String {
        specificationRowService.post(SpecificationRow(null, id, "", "", "", "", "", "|", "ABC", "1245", "1244", "", "Something", "[a == b] \"\" [] \"\"", "", "", "This is a comment", 0))
        return "redirect:/specifications/view/$id"
    }

    /**
     * Duplicates all rows from the rowId to the next segment or segment group.
     *
     * @param id The ID of the specification being modified.
     * @param rowId The ID of the row being modified.
     */
    @PostMapping("/specifications/view/{id}/duplicate/{rowId}")
    fun duplicate(@PathVariable("id") id: String, @PathVariable("rowId") rowId: String) : String {
        specificationRowService.duplicateRowsFromIdToNextSegment(id, rowId)

        return "redirect:/specifications/view/$id"
    }

    /**
     * Generates and downloads a Java file from the specification.
     *
     * Gets all of the specification rows for the specification, converts them into a CSV string, then generates and
     * downloads a Java file.
     *
     * @param id The ID of the specification being generated.
     */
    @GetMapping("/specifications/view/{id}/generate", produces = [MediaType.APPLICATION_OCTET_STREAM_VALUE])
    @ResponseBody
    fun generate(@PathVariable id: String, response: HttpServletResponse) : FileSystemResource {
        val rows = specificationRowService.findSpecificationRows(id)
        val builder = StringBuilder()

        // Build CSV
        rows.subList(1, rows.size).forEach {
            builder.append(it.seg_group + ",")
            builder.append(it.depth_5 + ",")
            builder.append(it.depth_4 + ",")
            builder.append(it.depth_3 + ",")
            builder.append(it.depth_2 + ",")
            builder.append(it.depth_1 + ",")
            builder.append(it.segment + ",")
            builder.append(it.element + ",")
            builder.append(it.sub_element + ",")
            builder.append(it.component + ",")
            builder.append(it.field_name + ",")
            builder.append(it.arsecode + ",")
            builder.append(it.field_count + ",")
            builder.append(it.looping_logic + ",")

            if(rows.last() != it) {
                builder.append("\n")
            }
        }

        // Build variable CSV
        val variables = variableService.getVariablesAsCsv()

        // Build name from message type, version, and proper name
        val specForGeneration = specificationService.findByIdOrNull(id)!!
        val generatorSpecName = "${specForGeneration.message_type}_${specForGeneration.version}" +
                if(specForGeneration.specification_name.isNotBlank()) "_${specForGeneration.specification_name}" else ""

        // Generate schema
        val filename = specificationService.generate(builder.toString(), generatorSpecName, variables)

        response.setHeader("Content-Disposition", "attachment; filename=${filename.split("/")[1]}");

        // Download
        return FileSystemResource(filename)
    }

    /**
     * Saves all of the rows in a specification.
     *
     * @param specificationRows All of the rows in a specification, passed through as a model attribute.
     * @param id The ID of the specification being saved.
     */
    @RequestMapping("/specifications/view/{id}/save")
    fun saveSpecification(@ModelAttribute specificationRows: SpecificationRows, @PathVariable id: String) : String {

        // Save each row
        specificationRows.rows.forEach {
            specificationRowService.post(it)
        }

        // Redirect back to the specification
        return "redirect:/specifications/view/$id"
    }

}