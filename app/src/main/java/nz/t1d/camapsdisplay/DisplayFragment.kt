package nz.t1d.camapsdisplay

import android.content.res.Resources
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.text.bold
import androidx.core.text.buildSpannedString
import androidx.core.text.color
import androidx.core.text.italic
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.setMargins
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import nz.t1d.camapsdisplay.databinding.FragmentDisplayBinding
import nz.t1d.di.BGLReading
import nz.t1d.di.BaseDataClass
import nz.t1d.di.BolusInsulin
import nz.t1d.di.CarbIntake
import nz.t1d.di.DiasendPoller
import nz.t1d.di.DisplayDataRepository
import okhttp3.internal.wait
import java.text.NumberFormat
import javax.inject.Inject

@AndroidEntryPoint
class DisplayFragment : Fragment() {

    @Inject
    lateinit var ddr: DisplayDataRepository

    @Inject
    lateinit var diasendPoller: DiasendPoller

    private val ddrListener = { -> updateValues() }

    private var _binding: FragmentDisplayBinding? = null

    private val nf2dp = NumberFormat.getNumberInstance()
    private val nf1dp = NumberFormat.getNumberInstance()
    private val nf0dp = NumberFormat.getNumberInstance()

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentDisplayBinding.inflate(inflater, container, false)

        nf2dp.maximumFractionDigits = 2
        nf1dp.maximumFractionDigits = 1
        nf0dp.maximumFractionDigits = 0

        binding.swiperefresh.setOnRefreshListener {
            val job = diasendPoller.fetchData()
            job.invokeOnCompletion {  binding.swiperefresh.isRefreshing = false }
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
        if (ddr.bglReadings.size > 0) {
            val first = ddr.bglReadings.first()
            val imageID = first.directionImageId()
            if (imageID != null) {
                binding.bglImage.setImageDrawable(ResourcesCompat.getDrawable(requireContext().resources, imageID, null))
            }

            binding.bglReading.text = first.value.toString()
            binding.bglUnits.text = first.bglUnit
            binding.bglTime.text = first.minsAgoString()
            binding.bglDiff.text = first.diffString()
        }

        // diasend values
        binding.iobbolustv.text = "${nf1dp.format(ddr.insulinOnBoardBolus)}u"
        binding.iobbasaltv.text = "${nf1dp.format(ddr.insulinOnBoardBasal)}u"
        binding.TIRtv.text = "${nf0dp.format(ddr.timeInRange * 100)}%"
        binding.basaltv.text = "${nf2dp.format(ddr.insulinCurrentBasal)}u"
        binding.meanstdtv.text = "${nf1dp.format(ddr.meanBGL)}/${nf1dp.format(ddr.stdBGL)}"

        // Build the list of recent events
        binding.recentEventRows.removeAllViews()
        for (re in ddr.recentEvents) {
            binding.recentEventRows.addView(createRecentEventRow(re))
        }
        binding.recentBglRows.removeAllViews()
        for (re in ddr.bglReadings.take(5)) {
            binding.recentBglRows.addView(createRecentEventRow(re))
        }
    }

    private fun createRecentEventRow(re: BaseDataClass): View {

        val row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val layoutParams = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT
        )
        layoutParams.setMargins(5)
        row.layoutParams = layoutParams

        val lps = { ->
            val lp = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(10, 1, 3, 0)
            lp
        }
        val image = ImageView(context)
        val text = TextView(context)

        // from https://stackoverflow.com/questions/4605527/converting-pixels-to-dp
        val dip = 25f
        val r: Resources = resources
        val px = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dip,
            r.displayMetrics
        )

        image.layoutParams = RelativeLayout.LayoutParams(px.toInt(), px.toInt())

        text.layoutParams = lps()

        val dBolus = ResourcesCompat.getDrawable(requireContext().resources, R.drawable.ic_bolus, null)

        val dCarb = ResourcesCompat.getDrawable(requireContext().resources, R.drawable.ic_carb, null)


        when (re) {
            is BolusInsulin -> {
                image.setImageDrawable(dBolus)
                text.text = buildSpannedString {
                    italic { append(re.minsAgoString()) }
                    append(" -- ")
                    bold { color(ResourcesCompat.getColor(requireContext().resources, R.color.teal_700, null)) { append("${re.value}u") } }
                    italic { append(" (${nf1dp.format(re.valueAfterDecay())}u)") }
                    append(" bolus")
                }
            }
            is CarbIntake -> {
                image.setImageDrawable(dCarb)
                text.text = buildSpannedString {
                    italic { append(re.minsAgoString()) }
                    append(" -- ")
                    bold { color(ResourcesCompat.getColor(requireContext().resources, R.color.teal_700, null)) { append("${re.value}g") } }
                    append(" carbs")
                }
            }
            is BGLReading -> {
                if (re.directionImageId() != null) {
                    image.setImageDrawable(ResourcesCompat.getDrawable(requireContext().resources, re.directionImageId()!!, null))
                }
                text.text = buildSpannedString {
                    italic { append(re.minsAgoString()) }
                    append(" -- ")
                    bold { color(ResourcesCompat.getColor(requireContext().resources, R.color.teal_700, null)) { append("${re.value}mmol/L ") } }
                    italic { append("(${re.diffString(false)})") }
                }
            }
        }

        text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20f)
        row.addView(image)
        row.addView(text)

        return row

    }

    override fun onDestroyView() {
        super.onDestroyView()
        ddr.listeners.remove(ddrListener) // must remove listener to stop null binding
        _binding = null
    }


}