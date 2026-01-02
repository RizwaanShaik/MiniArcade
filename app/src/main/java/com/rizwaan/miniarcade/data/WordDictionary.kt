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
     */
    fun initialize(context: Context) {
        if (isLoaded) return
        
        try {
            val jsonString = context.assets.open("words.json").bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            
            // Parse each word length category
            for (length in 3..7) {
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
     * Level 1-2: 3 letters
     * Level 3-4: 4 letters
     * Level 5-6: 5 letters
     * Level 7-8: 6 letters
     * Level 9+: 7 letters
     */
    fun getWordForLevel(level: Int): Pair<String, String> {
        val wordLength = when {
            level <= 2 -> 3
            level <= 4 -> 4
            level <= 6 -> 5
            level <= 8 -> 6
            else -> 7
        }
        
        val groups = wordsByLength[wordLength]
        if (groups.isNullOrEmpty()) {
            // Fallback if dictionary not loaded
            return "CAT" to "A pet that meows 🐱"
        }
        
        val group = groups.random()
        return group.primaryWord to group.hint
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
