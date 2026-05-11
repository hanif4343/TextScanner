package com.hanif.textscanner.util

object McqParser {

    data class McqQuestion(
        val question: String,
        val options: List<String>
    )

    /**
     * Parse MCQ questions from OCR text.
     * Handles both Bengali and English MCQ formats:
     * - Numbered: 1. / ১. / Q1.
     * - Options: a) b) c) d) / ক) খ) গ) ঘ) / (a) (b) / A. B. C. D.
     */
    fun parse(text: String): List<McqQuestion> {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val questions = mutableListOf<McqQuestion>()

        var currentQuestion: StringBuilder? = null
        var currentOptions = mutableListOf<String>()

        // Patterns for question numbers (English and Bengali digits)
        val questionPattern = Regex(
            "^([০-৯\\d]+[।\\.\\)]\\s*.+|[Qq][\\d০-৯]+[\\.:।]\\s*.+|প্রশ্ন\\s*[০-৯\\d]+.+)"
        )

        // Patterns for options
        val optionPattern = Regex(
            "^([ক-ঘ][)।\\.]\\s*.+|[a-dA-D][)।\\.]\\s*.+|\\([a-dA-Dক-ঘ]\\)\\s*.+|[①②③④]\\s*.+)"
        )

        for (line in lines) {
            when {
                optionPattern.containsMatchIn(line) -> {
                    currentOptions.add(line)
                }
                questionPattern.containsMatchIn(line) -> {
                    // Save previous Q+options
                    if (currentQuestion != null && currentOptions.isNotEmpty()) {
                        questions.add(McqQuestion(currentQuestion.toString().trim(), currentOptions.toList()))
                    } else if (currentQuestion != null && currentOptions.isEmpty()) {
                        // might be a regular question, check later
                    }
                    currentQuestion = StringBuilder(line)
                    currentOptions = mutableListOf()
                }
                currentQuestion != null && currentOptions.isEmpty() -> {
                    // continuation of question text
                    currentQuestion?.append(" $line")
                }
            }
        }

        // Last question
        if (currentQuestion != null && currentOptions.isNotEmpty()) {
            questions.add(McqQuestion(currentQuestion.toString().trim(), currentOptions.toList()))
        }

        return questions
    }

    /**
     * Format MCQ nicely for display/export
     */
    fun formatMcq(questions: List<McqQuestion>): String {
        val sb = StringBuilder()
        questions.forEachIndexed { i, q ->
            sb.append("${i + 1}. ${q.question}\n")
            q.options.forEach { opt -> sb.append("   $opt\n") }
            sb.append("\n")
        }
        return sb.toString()
    }
}
