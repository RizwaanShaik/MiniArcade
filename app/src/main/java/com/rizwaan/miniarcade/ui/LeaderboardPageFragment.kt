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
import kotlinx.coroutines.launch

class LeaderboardPageFragment : Fragment() {

    private var _binding: FragmentLeaderboardPageBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var firebaseRepo: FirebaseRepository
    private lateinit var leaderboardAdapter: LeaderboardAdapter
    private var gameType: GameType = GameType.REACTION_TIME

    companion object {
        private const val ARG_GAME_TYPE = "game_type"
        
        fun newInstance(gameType: GameType): LeaderboardPageFragment {
            return LeaderboardPageFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_GAME_TYPE, gameType.name)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.getString(ARG_GAME_TYPE)?.let {
            gameType = GameType.valueOf(it)
        }
    }

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
        loadLeaderboard()
    }

    private fun setupRecyclerView() {
        leaderboardAdapter = LeaderboardAdapter(gameType)
        binding.rvLeaderboard.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = leaderboardAdapter
        }
    }

    private fun loadLeaderboard() {
        binding.progressBar.visibility = View.VISIBLE
        binding.emptyState.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            firebaseRepo.getLeaderboard(gameType, 10).collectLatest { scores ->
                binding.progressBar.visibility = View.GONE

                if (scores.isEmpty()) {
                    binding.emptyState.visibility = View.VISIBLE
                    binding.rvLeaderboard.visibility = View.GONE
                } else {
                    binding.emptyState.visibility = View.GONE
                    binding.rvLeaderboard.visibility = View.VISIBLE
                    leaderboardAdapter.updateGameType(gameType)
                    leaderboardAdapter.submitList(scores)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

