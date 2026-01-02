package com.rizwaan.miniarcade.ui

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.rizwaan.miniarcade.data.models.GameType
import com.rizwaan.miniarcade.databinding.ActivityLeaderboardBinding

class LeaderboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLeaderboardBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        binding = ActivityLeaderboardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        setupBackButton()
        setupViewPager()
    }
    
    private fun setupBackButton() {
        binding.btnBack.setOnClickListener {
            finish()
        }
    }
    
    private fun setupViewPager() {
        val gameTypes = GameType.entries.toList()
        
        // Set up ViewPager2 with FragmentStateAdapter
        // First tab is "Combined", then individual games
        binding.viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = gameTypes.size + 1 // +1 for combined
            
            override fun createFragment(position: Int): Fragment {
                return if (position == 0) {
                    CombinedLeaderboardFragment()
                } else {
                    LeaderboardPageFragment.newInstance(gameTypes[position - 1])
                }
            }
        }
        
        // Connect TabLayout with ViewPager2
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            if (position == 0) {
                tab.text = "🏆 Total"
            } else {
                val gameType = gameTypes[position - 1]
                tab.text = "${gameType.emoji} ${gameType.displayName}"
            }
        }.attach()
    }
}
