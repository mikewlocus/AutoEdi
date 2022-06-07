package com.locussoftware.arse.ae.services

import com.locussoftware.arse.ae.entities.Change
import com.locussoftware.arse.ae.repositories.ChangeRepository
import org.springframework.stereotype.Service

@Service
class ChangeService (val db: ChangeRepository) {

    fun findChanges() : List<Change> = db.findChanges()

    fun post(change: Change) {
        db.save(change)
    }

}