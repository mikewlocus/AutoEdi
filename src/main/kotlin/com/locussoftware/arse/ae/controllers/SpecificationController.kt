package com.locussoftware.arse.ae.controllers

import com.locussoftware.arse.ae.ImportSpecification
import com.locussoftware.arse.ae.SpecificationRows
import com.locussoftware.arse.ae.entities.Specification
import com.locussoftware.arse.ae.entities.SpecificationRow
import com.locussoftware.arse.ae.getRowsFromCsv
import com.locussoftware.arse.ae.services.SpecificationRowService
import com.locussoftware.arse.ae.services.SpecificationService
import org.springframework.core.io.FileSystemResource
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.ui.set
import org.springframework.util.FileCopyUtils
import org.springframework.web.bind.annotation.*
import org.springframework.web.bind.WebDataBinder

import org.springframework.web.bind.annotation.InitBinder
import java.io.BufferedInputStream
import java.io.FileInputStream
import java.io.InputStream
import javax.servlet.http.HttpServletResponse


@Controller
class SpecificationController (val specificationService: SpecificationService,
                               val specificationRowService: SpecificationRowService) {

    @InitBinder
    fun initBinder(binder: WebDataBinder) {
        binder.autoGrowCollectionLimit = 2000000000
    }

    @GetMapping
    fun index() : String {
        return "/index"
    }

    @GetMapping("/specifications")
    fun specifications(model: Model) : String {
        model["specifications"] = specificationService.findSpecifications()
        model["specification"] = Specification(null, "")
        model["importSpec"] = ImportSpecification("", "")
        return "/specifications"
    }

    @RequestMapping("/specifications/view/{id}")
    fun specificationRows(@PathVariable id: String, model: Model) : String {
        model["specificationRows"] = SpecificationRows(specificationRowService.findSpecificationRows(id).sortedBy { it.row_index })
        model["specificationId"] = id
        model["specification"] = specificationService.findByIdOrNull(id)!!
        return "/spec-editor"
    }

    @RequestMapping("/specifications/delete/{id}")
    fun specificationRows(@PathVariable id: String) : String {
        val specToDelete = specificationService.findByIdOrNull(id)

        if(specToDelete != null) {
            specificationRowService.deleteAllRowsInSpecification(id)
            specificationService.delete(specToDelete)
        }

        return "redirect:/specifications"
    }

    @PostMapping("/specifications/post")
    fun postSpecification(@ModelAttribute specification: Specification) : String {
        specificationService.post(specification)
        return "redirect:/specifications"
    }

    @PostMapping("/specifications/import")
    fun importSpecification(@ModelAttribute importSpec: ImportSpecification) : String {

        // Create specification
        specificationService.post(Specification(null, importSpec.specification_name))

        val spec = specificationService.findByNameOrNull(importSpec.specification_name)

        // Process CSV
        if(spec?.id != null) {
            getRowsFromCsv(importSpec.csv.split("\n"), spec.id).forEach {
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
     * Gets all of the specification rows for the specification, converts them into a CSV string, then generates and
     * downloads a Java file.
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

        // Generate schema
        val filename = specificationService.generate(builder.toString(), specificationService.findByIdOrNull(id)!!.specification_name)

        response.setHeader("Content-Disposition", "attachment; filename=${filename.split("/")[1]}");

        // Download
        return FileSystemResource(filename)
    }

    @RequestMapping("/specifications/view/{id}/save")
    fun saveSpecification(@ModelAttribute specificationRows: SpecificationRows, @PathVariable id: String) : String {

        specificationRows.rows.forEach {
            specificationRowService.post(it)
        }

        return "redirect:/specifications/view/$id"
    }

}