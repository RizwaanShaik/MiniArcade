package com.rizwaan.miniarcade.data

import android.content.Context
import org.json.JSONObject

/**
 * Word Dictionary for Word Scramble Game
 * 
 * Loads words from assets/words.json
 * Handles anagram groups - any valid anagram in a group is accepted as correct
 */
object WordDictionary {
    
    // Data class to hold a word group (anagrams with the same hint)
    data class WordGroup(
        val words: List<String>,  // All valid anagrams
        val hint: String,
        val primaryWord: String   // The main word shown (first in list)
    ) {
        // Get the anagram signature (sorted letters) for this group
        val signature: String = primaryWord.uppercase().toList().sorted().joinToString("")
    }
    
    // Words organized by length, then by groups
    private val wordsByLength = mutableMapOf<Int, MutableList<WordGroup>>()
    
    // Quick lookup: signature -> word group
    private val signatureToGroup = mutableMapOf<String, WordGroup>()
    
    // All valid words set for quick lookup
    private val allValidWords = mutableSetOf<String>()
    
    // Track if already loaded
    private var isLoaded = false
    
    /**
     * Initialize the dictionary from the JSON file
     * Call this once from Application or first Activity
     * 
     * Note: words.json is pre-ordered so the first word matches the hint
     */
    fun initialize(context: Context) {
        if (isLoaded) return
        
        try {
            val jsonString = context.assets.open("words.json").bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            
            // Parse each word length category (support all lengths 3-14)
            for (length in 3..14) {
                val lengthKey = length.toString()
                if (jsonObject.has(lengthKey)) {
                    val wordArray = jsonObject.getJSONArray(lengthKey)
                    val groups = mutableListOf<WordGroup>()
                    
                    for (i in 0 until wordArray.length()) {
                        val groupObj = wordArray.getJSONObject(i)
                        val wordsArray = groupObj.getJSONArray("words")
                        val hint = groupObj.getString("hint")
                        
                        val words = mutableListOf<String>()
                        for (j in 0 until wordsArray.length()) {
                            val word = wordsArray.getString(j).uppercase()
                            words.add(word)
                            allValidWords.add(word)
                        }
                        
                        if (words.isNotEmpty()) {
                            // First word is guaranteed to match the hint (pre-ordered)
                            val group = WordGroup(
                                words = words,
                                hint = hint,
                                primaryWord = words.first()
                            )
                            groups.add(group)
                            
                            // Map signature to group for anagram checking
                            signatureToGroup[group.signature] = group
                        }
                    }
                    
                    wordsByLength[length] = groups
                }
            }
            
            isLoaded = true
            android.util.Log.d("WordDictionary", "Loaded ${allValidWords.size} words across ${wordsByLength.size} lengths")
            
        } catch (e: Exception) {
            android.util.Log.e("WordDictionary", "Failed to load words.json", e)
            // Fall back to empty dictionary
        }
    }
    
    /**
     * Get a random word group for a given level
     * 
     * Difficulty progression:
     * - Levels 1-3: 3 letters (easy start)
     * - Levels 4-7: 4 letters (gradual increase)
     * - Levels 8-12: 5 letters (moderate)
     * - Levels 13-18: 6 letters (challenging)
     * - Levels 19-25: 7 letters (hard)
     * - Levels 26-35: 8 letters (very hard)
     * - Levels 36-50: 9-10 letters (expert)
     * - Levels 51+: 11-14 letters (master)
     * 
     * Uses smooth progression with some randomness for variety
     */
    fun getWordForLevel(level: Int): Pair<String, String> {
        // Calculate base word length with smooth progression
        val wordLength = calculateWordLength(level)
        
        // Get available groups for this length
        val groups = wordsByLength[wordLength]
        if (groups.isNullOrEmpty()) {
            // Try to find the closest available length
            val availableLengths = wordsByLength.keys.sorted()
            if (availableLengths.isEmpty()) {
                // Fallback if dictionary not loaded
                return "CAT" to "A pet that meows 🐱"
            }
            
            // Find closest available length
            val closestLength = availableLengths.minByOrNull { 
                kotlin.math.abs(it - wordLength) 
            } ?: availableLengths.first()
            
            val fallbackGroups = wordsByLength[closestLength]
            if (fallbackGroups.isNullOrEmpty()) {
                return "CAT" to "A pet that meows 🐱"
            }
            
            val group = fallbackGroups.random()
            return group.primaryWord to group.hint
        }
        
        val group = groups.random()
        return group.primaryWord to group.hint
    }
    
    /**
     * Calculate word length based on level with smooth progression
     */
    private fun calculateWordLength(level: Int): Int {
        return when {
            // Early levels: 3 letters (levels 1-3)
            level <= 3 -> 3
            
            // Easy progression: 4 letters (levels 4-7)
            level <= 7 -> 4
            
            // Moderate: 5 letters (levels 8-12)
            level <= 12 -> 5
            
            // Challenging: 6 letters (levels 13-18)
            level <= 18 -> 6
            
            // Hard: 7 letters (levels 19-25)
            level <= 25 -> 7
            
            // Very hard: 8 letters (levels 26-35)
            level <= 35 -> 8
            
            // Expert: 9-10 letters (levels 36-50)
            level <= 50 -> {
                // Mix of 9 and 10 letter words
                if (level % 2 == 0) 9 else 10
            }
            
            // Master: 11-14 letters (levels 51+)
            else -> {
                // Gradually increase from 11 to 14
                val extraLevels = level - 50
                when {
                    extraLevels <= 10 -> 11
                    extraLevels <= 20 -> 12
                    extraLevels <= 30 -> 13
                    else -> 14
                }
            }
        }
    }
    
    /**
     * Check if the user's answer is correct
     * Returns true if:
     * 1. It matches the target word exactly, OR
     * 2. It's a valid anagram that exists in the same anagram group
     */
    fun isCorrectAnswer(userAnswer: String, targetWord: String): Boolean {
        val answer = userAnswer.uppercase()
        val target = targetWord.uppercase()
        
        // Exact match
        if (answer == target) return true
        
        // Check if it's a valid anagram in the same group
        val targetSignature = target.toList().sorted().joinToString("")
        val answerSignature = answer.toList().sorted().joinToString("")
        
        // Must have same letters
        if (targetSignature != answerSignature) return false
        
        // Check if the answer is in the same word group
        val group = signatureToGroup[targetSignature]
        return group?.words?.contains(answer) == true
    }
    
    /**
     * Check if a word exists in the dictionary
     */
    fun isValidWord(word: String): Boolean {
        return allValidWords.contains(word.uppercase())
    }
    
    /**
     * Check if two words are anagrams of each other
     */
    fun areAnagrams(word1: String, word2: String): Boolean {
        return word1.uppercase().toList().sorted() == word2.uppercase().toList().sorted()
    }
    
    /**
     * Get all words as a set for validation
     */
    fun getAllWords(): Set<String> = allValidWords.toSet()
    
    /**
     * Get total word count
     */
    fun getTotalWordCount(): Int = allValidWords.size
    
    /**
     * Get word count by length
     */
    fun getWordCountByLength(length: Int): Int {
        return wordsByLength[length]?.sumOf { it.words.size } ?: 0
    }
}
