package com.mimotts.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.mimotts.app.databinding.ActivityMainBinding
import com.mimotts.app.ui.clone.CloneFragment
import com.mimotts.app.ui.design.DesignFragment
import com.mimotts.app.ui.preset.PresetFragment

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private val tabTitles = arrayOf("预置音色", "音色克隆", "音色设计")
    private val fragments: List<() -> Fragment> = listOf(
        { PresetFragment() },
        { CloneFragment() },
        { DesignFragment() }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        binding.viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = fragments.size
            override fun createFragment(position: Int) = fragments[position]()
        }

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, pos ->
            tab.text = tabTitles[pos]
        }.attach()
    }
}
