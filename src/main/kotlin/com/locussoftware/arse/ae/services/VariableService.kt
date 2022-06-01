package com.locussoftware.arse.ae.services

import com.locussoftware.arse.ae.entities.Variable
import com.locussoftware.arse.ae.repositories.VariableRepository
import org.springframework.stereotype.Service
import java.io.BufferedReader
import java.io.File
import java.lang.StringBuilder

@Service
class VariableService(val db: VariableRepository) {

    /**
     * Loads variables from the CSV if the database table is empty, then returns a list of variables from the database.
     */
    fun getVariables() : List<Variable> {
        // Load from CSV if empty
        if(db.count() == 0L) {
            this.loadVariablesFromCsv()
        }

        return db.findVariables()
    }

    /**
     * Reads through the variable CSV, imports each row into the variables table.
     */
    private fun loadVariablesFromCsv() {
        // Load input from file
        val bufferedCustomFieldReader: BufferedReader = File("variables.csv").bufferedReader()
        val customFieldCsv = bufferedCustomFieldReader.use { it.readText() }

        val fields = customFieldCsv
            .replace("\"\"", "&dQuot1653061221")
            .replace("\"", "")
            .replace("&dQuot1653061221", "\"")
            .split(System.lineSeparator())

        // Loop through fields, save to db
        fields.forEach {
            val splitField = it.split(",")

            val descIdx = 0
            val nameIdx = 1
            val codeIdx = 2
            val typeIdx = 3
            val checkIdx = 4
            val paramReqIdx = 5
            val paramInIdx = 6
            val addCheckIdx = 8

            if(splitField.size > addCheckIdx) {
                db.save(Variable(null,
                    splitField[descIdx],
                    splitField[nameIdx],
                    splitField[codeIdx],
                    splitField[typeIdx],
                    splitField[checkIdx],
                    splitField[paramReqIdx],
                    splitField[paramInIdx],
                    splitField[addCheckIdx]))
            }
        }
    }

    /**
     * Builds a CSV String from all of the variable rows in the DB.
     *
     * @return A CSV String, built from the variables in the DB.
     */
    fun getVariablesAsCsv() : String {
        val builder = StringBuilder()
        val variables = this.getVariables()

        variables.forEach {
            builder.append(it.description + ",")
            builder.append(it.var_name + ",")
            builder.append(it.code + ",")
            builder.append(it.var_type + ",")
            builder.append(it.null_check + ",")
            builder.append(it.var_params + ",")
            builder.append(it.required_params + ",,") // Extra comma needed for blank column
            builder.append(it.additional_checks)

            if(variables.last() != it) {
                builder.append("\n")
            }
        }

        return builder.toString()
    }

}