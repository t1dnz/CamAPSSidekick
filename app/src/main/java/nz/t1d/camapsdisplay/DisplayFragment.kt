package nz.t1d.camapsdisplay

import android.os.Bundle
import android.text.format.DateUtils
import android.view.*
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint

import nz.t1d.camapsdisplay.databinding.FragmentDisplayBinding
import nz.t1d.di.DisplayDataRepository
import javax.inject.Inject

@AndroidEntryPoint
class DisplayFragment : Fragment() {

    @Inject
    lateinit var ddr: DisplayDataRepository
    private val ddrListener = { -> updateValues() }

    private var _binding: FragmentDisplayBinding? = null


    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentDisplayBinding.inflate(inflater, container, false)


        // sets up the minutes ago text field

        binding.bglTime.start()
        binding.bglTime.setOnChronometerTickListener { chrono ->
            chrono.text = DateUtils.getRelativeTimeSpanString(chrono.base)
        }

        // Setup the actions on the menu when the fragment is open
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                val act = (activity as MainActivity).supportActionBar!!
                act.setDisplayHomeAsUpEnabled(true)
                act.setDisplayShowHomeEnabled(true)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                // Handle the menu selection
                return when (menuItem.itemId) {
                    android.R.id.home -> {
                        findNavController().navigate(R.id.action_navDisplayFragment_to_navSettingsFragment)
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)

        updateValues()

        ddr.listeners.add(ddrListener)
        return binding.root
    }


    fun updateValues() {
        binding.bglImage.setImageDrawable(ddr.bglDirectionImage)
        binding.bglReading.text = ddr.bglReading.toString()
        binding.bglUnits.text = ddr.bglUnit
        binding.bglTime.base = ddr.bglReadingTime
        binding.bglDiff.text = ddr.bglDiff
        binding.iob.text = ddr.insulinOnBoard.toString()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.bglTime.stop()
        ddr.listeners.remove(ddrListener) // must remove listener to stop null binding
        _binding = null
    }


}