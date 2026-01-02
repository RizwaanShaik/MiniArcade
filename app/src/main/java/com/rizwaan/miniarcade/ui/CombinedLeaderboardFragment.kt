package com.rizwaan.miniarcade.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.rizwaan.miniarcade.data.models.GameType
import com.rizwaan.miniarcade.data.repository.FirebaseRepository
import com.rizwaan.miniarcade.databinding.FragmentLeaderboardPageBinding
import com.rizwaan.miniarcade.ui.adapters.LeaderboardAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class CombinedLeaderboardFragment : Fragment() {

    private var _binding: FragmentLeaderboardPageBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var firebaseRepo: FirebaseRepository
    private lateinit var leaderboardAdapter: LeaderboardAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLeaderboardPageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        firebaseRepo = FirebaseRepository()
        
        setupRecyclerView()
        loadCombinedLeaderboard()
    }

    private fun setupRecyclerView() {
        // Use REACTION_TIME as default for adapter (it will show combined scores)
        leaderboardAdapter = LeaderboardAdapter(GameType.REACTION_TIME)
        binding.rvLeaderboard.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = leaderboardAdapter
        }
    }

    private fun loadCombinedLeaderboard() {
        binding.progressBar.visibility = View.VISIBLE
        binding.emptyState.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Get all game types
                val allGameTypes = GameType.entries.toList()
                
                // Collect scores from all games
                val allScores = mutableMapOf<String, CombinedPlayerScore>()
                
                allGameTypes.forEach { gameType ->
                    val scores = firebaseRepo.getLeaderboard(gameType, 100).firstOrNull() ?: emptyList()
                    scores.forEach { gameScore ->
                        val playerId = gameScore.playerId
                        val existing = allScores[playerId]
                        
                        // For Reaction Time, lower is better - convert to points (10000 - time)
                        // For Memory Flip, lower is better - convert to points (10000 - moves)
                        // For others, higher is better - use score directly
                        val points = when (gameType) {
                            GameType.REACTION_TIME -> {
                                if (gameScore.score > 0) 10000L - gameScore.score else 0L
                            }
                            GameType.MEMORY_FLIP -> {
                                if (gameScore.score > 0) 10000L - gameScore.score else 0L
                            }
                            else -> gameScore.score
                        }
                        
                        if (existing != null) {
                            existing.totalScore += points
                            existing.gamesPlayed++
                        } else {
                            allScores[playerId] = CombinedPlayerScore(
                                playerId = playerId,
                                playerUsername = gameScore.playerUsername,
                                playerAvatar = gameScore.playerAvatar,
                                totalScore = points,
                                gamesPlayed = 1
                            )
                        }
                    }
                }
                
                // Convert to list and sort by total score (descending)
                val combinedScores = allScores.values
                    .sortedByDescending { it.totalScore }
                    .take(50) // Top 50 players
                    .mapIndexed { index, combined ->
                        // Convert back to GameScore format for adapter
                        com.rizwaan.miniarcade.data.models.GameScore(
                            id = combined.playerId,
                            playerId = combined.playerId,
                            playerUsername = combined.playerUsername,
                            playerAvatar = combined.playerAvatar,
                            gameType = GameType.REACTION_TIME, // Use as placeholder for combined
                            score = combined.totalScore,
                            timestamp = System.currentTimeMillis(),
                            extras = mapOf("gamesPlayed" to combined.gamesPlayed)
                        )
                    }
                
                binding.progressBar.visibility = View.GONE
                
                if (combinedScores.isEmpty()) {
                    binding.emptyState.visibility = View.VISIBLE
                    binding.rvLeaderboard.visibility = View.GONE
                } else {
                    binding.emptyState.visibility = View.GONE
                    binding.rvLeaderboard.visibility = View.VISIBLE
                    leaderboardAdapter.updateGameType(GameType.REACTION_TIME)
                    leaderboardAdapter.submitList(combinedScores)
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.emptyState.visibility = View.VISIBLE
                binding.rvLeaderboard.visibility = View.GONE
            }
        }
    }
    
    private data class CombinedPlayerScore(
        val playerId: String,
        val playerUsername: String,
        val playerAvatar: String,
        var totalScore: Long,
        var gamesPlayed: Int
    )

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

