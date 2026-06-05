package com.example

import android.util.Log

object LootEvaluator {
    private const val TAG = "AutoNextLootEvaluator"

    /**
     * Parses the unstructured text string returned by ML Kit OCR into Gold and Elixir numbers.
     * Handles digits separated by spaces (e.g. "1 077 403" -> 1077403) and handles common OCR misidentifications.
     */
    fun parseLoot(ocrText: String): Pair<Long, Long> {
        Log.d(TAG, "Parsing OCR text: \"$ocrText\"")
        val lines = ocrText.split("\n")
        val matches = mutableListOf<Long>()
        
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            
            // Normalize common OCR misidentifications
            val cleanedLine = trimmed
                .replace("o", "0")
                .replace("O", "0")
                .replace("Q", "0")
                .replace("D", "0")
                .replace("I", "1")
                .replace("l", "1")
                .replace("|", "1")
                .replace("i", "1")
                .replace("T", "1")
                .replace("t", "1")
                .replace("S", "5")
                .replace("s", "5")
                .replace("B", "8")
                .replace("G", "6")
                .replace("g", "9")
                .replace("b", "6")
                .replace("z", "2")
                .replace("Z", "2")
                .replace(",", "")
                .replace(".", "")
            
            // Reconnect digit sequences separated by spaces (e.g. "1 077 403" -> "1077403")
            val healedLine = cleanedLine.replace("(?<=\\d)\\s+(?=\\d)".toRegex(), "")
            
            // Capture any sequences of 4 to 9 digits
            val numberRegex = "\\d{4,9}".toRegex()
            val lineMatches = numberRegex.findAll(healedLine).map { it.value.toLongOrNull() ?: 0L }.toList()
            for (m in lineMatches) {
                if (m > 0L) {
                    matches.add(m)
                }
            }
        }
        
        Log.d(TAG, "Line-by-line matches found: $matches")
        
        // If line-by-line processing didn't find enough entries, run global healed search
        if (matches.size < 2) {
            val globalPreClean = ocrText
                .replace("o", "0")
                .replace("O", "0")
                .replace("Q", "0")
                .replace("D", "0")
                .replace("I", "1")
                .replace("l", "1")
                .replace("|", "1")
                .replace("i", "1")
                .replace("T", "1")
                .replace("t", "1")
                .replace("S", "5")
                .replace("s", "5")
                .replace("B", "8")
                .replace("G", "6")
                .replace("g", "9")
                .replace("b", "6")
                .replace("z", "2")
                .replace("Z", "2")
                .replace(",", "")
                .replace(".", "")
            
            val globalHealed = globalPreClean.replace("(?<=\\d)\\s+(?=\\d)".toRegex(), "")
            val globalRegex = "\\b\\d{4,9}\\b".toRegex()
            val globalMatches = globalRegex.findAll(globalHealed).map { it.value.toLongOrNull() ?: 0L }.filter { it > 0L }.toList()
            
            Log.d(TAG, "Global fallback matches found: $globalMatches")
            if (globalMatches.size > matches.size) {
                matches.clear()
                matches.addAll(globalMatches)
            }
        }
        
        var gold = 0L
        var elixir = 0L
        
        if (matches.size >= 2) {
            gold = matches[0]
            elixir = matches[1]
        } else if (matches.size == 1) {
            gold = matches[0]
            elixir = matches[0]
        }
        
        Log.d(TAG, "Parsed Loot values -> Gold: $gold, Elixir: $elixir")
        return Pair(gold, elixir)
    }
}
