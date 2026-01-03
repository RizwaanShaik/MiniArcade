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
                // Use the stored totalScore from Firebase instead of computing it
                firebaseRepo.getTotalLeaderboard(50).collectLatest { combinedScores ->
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
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.emptyState.visibility = View.VISIBLE
                binding.rvLeaderboard.visibility = View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

