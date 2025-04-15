package com.parwar.german_learning.data.models

data class DialogPair(
    var germanQuestion: String = "",
    var germanAnswer: String = "",
    var englishQuestion: String = "",
    var englishAnswer: String = "",
    var id: Long = 0,
    var reviewCount: Int = 0,
    var lastReviewed: Long = 0,
    var difficulty: Float = 0.5f
) {
    constructor() : this("", "", "", "", 0, 0, 0, 0.5f)
}
