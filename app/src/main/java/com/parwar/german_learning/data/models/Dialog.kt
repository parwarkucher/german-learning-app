package com.parwar.german_learning.data.models

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.parwar.german_learning.utils.Converters

@Entity(tableName = "dialogs")
@TypeConverters(Converters::class)
data class Dialog(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0,
    var germanText: String = "",
    var englishText: String = "",
    @TypeConverters(Converters::class)
    var participants: List<String> = emptyList(),
    @TypeConverters(Converters::class)
    var tags: List<String> = emptyList(),
    var category: String = "",
    var difficulty: String = "Beginner",
    var contextNotes: String = "",
) {
    @Ignore
    var dialogPairs: List<DialogPair> = emptyList()

    constructor() : this(
        id = 0,
        germanText = "",
        englishText = "",
        participants = emptyList(),
        tags = emptyList(),
        category = "",
        difficulty = "Beginner",
        contextNotes = ""
    )

    @Ignore
    fun parseDialogPairs(): List<DialogPair> {
        if (germanText.isBlank() || englishText.isBlank()) {
            return emptyList()
        }

        val germanLines = germanText.trim().split("\n")
        val englishLines = englishText.trim().split("\n")
        
        val pairs = mutableListOf<DialogPair>()
        var currentGermanQ = ""
        var currentGermanA = ""
        var currentEnglishQ = ""
        var currentEnglishA = ""
        
        // Parse German text
        for (line in germanLines) {
            val trimmedLine = line.trim()
            when {
                trimmedLine.startsWith("Q:", ignoreCase = true) -> {
                    if (currentGermanQ.isNotEmpty() && currentGermanA.isNotEmpty()) {
                        pairs.add(DialogPair(currentGermanQ, currentGermanA, "", "", id))
                        currentGermanA = ""
                    }
                    currentGermanQ = trimmedLine.substringAfter(":").trim()
                }
                trimmedLine.startsWith("A:", ignoreCase = true) -> {
                    currentGermanA = trimmedLine.substringAfter(":").trim()
                }
            }
        }
        if (currentGermanQ.isNotEmpty() && currentGermanA.isNotEmpty()) {
            pairs.add(DialogPair(currentGermanQ, currentGermanA, "", "", id))
        }
        
        // Parse English text and match with German pairs
        var pairIndex = 0
        for (line in englishLines) {
            val trimmedLine = line.trim()
            when {
                trimmedLine.startsWith("Q:", ignoreCase = true) -> {
                    currentEnglishQ = trimmedLine.substringAfter(":").trim()
                }
                trimmedLine.startsWith("A:", ignoreCase = true) -> {
                    currentEnglishA = trimmedLine.substringAfter(":").trim()
                    if (currentEnglishQ.isNotEmpty() && pairIndex < pairs.size) {
                        pairs[pairIndex] = pairs[pairIndex].copy(
                            englishQuestion = currentEnglishQ,
                            englishAnswer = currentEnglishA
                        )
                        pairIndex++
                        currentEnglishQ = ""
                        currentEnglishA = ""
                    }
                }
            }
        }
        
        // Only return pairs that have both German and English translations
        return pairs.filter { pair ->
            pair.germanQuestion.isNotEmpty() && 
            pair.germanAnswer.isNotEmpty() && 
            pair.englishQuestion.isNotEmpty() && 
            pair.englishAnswer.isNotEmpty()
        }
    }
}
