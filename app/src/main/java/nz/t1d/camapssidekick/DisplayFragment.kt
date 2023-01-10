package nz.t1d.camapssidekick

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
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import nz.t1d.camapssidekick.databinding.FragmentDisplayBinding
import nz.t1d.datamodels.BGLReading
import nz.t1d.datamodels.BaseDataClass
import nz.t1d.datamodels.BolusInsulin
import nz.t1d.datamodels.CarbIntake
import nz.t1d.di.DiasendPoller
import nz.t1d.di.DisplayDataRepository
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
        nf2dp.minimumFractionDigits = 1

        nf1dp.maximumFractionDigits = 1
        nf1dp.minimumFractionDigits = 1

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
            val imageID = directionImageId(first)
            if (imageID != null) {
                binding.bglImage.setImageDrawable(ResourcesCompat.getDrawable(requireContext().resources, imageID, null))
            }

            binding.bglReading.text = first.value.toString()
            binding.bglUnits.text = first.bglUnit
            binding.bglTime.text = first.minsAgoString()
            binding.bglDiff.text = first.diffString()
        }

        // diasend values
        binding.iobtv.text = "${nf1dp.format(ddr.insulinOnBoardBolus)}u/${nf1dp.format(ddr.insulinOnBoardBasal)}u"
        binding.TIRtv.text = "${nf0dp.format(ddr.timeInRange * 100)}%"
        binding.basaltv.text = "${nf2dp.format(ddr.insulinCurrentBasal)}u/h"
        binding.meanstdtv.text = "${nf1dp.format(ddr.meanBGL)}/${nf1dp.format(ddr.stdBGL)}"

        // Build the list of recent events
        binding.recentEventRows.removeAllViews()
        for (re in ddr.recentEvents) {
            binding.recentEventRows.addView(createRecentEventRow(re))
        }
        binding.recentBglRows.removeAllViews()
        for (re in ddr.bglReadings.take(6)) {
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
        val time = TextView(context)
        val action = TextView(context)

        // from https://stackoverflow.com/questions/4605527/converting-pixels-to-dp
        val dip = 25f
        val r: Resources = resources
        val px = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dip,
            r.displayMetrics
        )

        image.layoutParams = RelativeLayout.LayoutParams(px.toInt(), px.toInt())

        time.layoutParams = lps()
        time.text = buildSpannedString {
            italic { append(re.minsAgoString()) }
        }
        time.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20f)
        time.setEms(5)

        action.layoutParams = lps()
        action.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20f)

        val dBolus = ResourcesCompat.getDrawable(requireContext().resources, R.drawable.ic_bolus, null)

        val dCarb = ResourcesCompat.getDrawable(requireContext().resources, R.drawable.ic_carb, null)


        when (re) {
            is BolusInsulin -> {
                image.setImageDrawable(dBolus)
                action.text = buildSpannedString {
                    bold { color(ResourcesCompat.getColor(requireContext().resources, R.color.teal_700, null)) { append("${re.value}u") } }
                    val ci = re.carbIntake
                    if (ci != null) {
                        append(" bolus")
                        italic { append(" for ") }
                        bold { color(ResourcesCompat.getColor(requireContext().resources, R.color.teal_700,null)) {  append("${nf0dp.format(ci.value)}g") } }
                        append(" carbs")
                    } else {
                        append(" correction")
                    }
                }


            }
            is CarbIntake -> {
                image.setImageDrawable(dCarb)
                action.text = buildSpannedString {
                    bold { color(ResourcesCompat.getColor(requireContext().resources, R.color.teal_700, null)) { append("${nf0dp.format(re.value)}g") } }
                    append(" carbs")
                }
            }
            is BGLReading -> {
                val di = directionImageId(re)
                if (di != null) {
                    image.setImageDrawable(ResourcesCompat.getDrawable(requireContext().resources, di!!, null))
                }
                action.text = buildSpannedString {
                    bold { color(ResourcesCompat.getColor(requireContext().resources, R.color.teal_700, null)) { append("${re.value}mmol/L ") } }
                    italic { append("(${re.diffString(false)})") }
                }
            }
        }


        row.addView(image)
        row.addView(time)
        row.addView(action)

//        var ag = ObjectAnimator.ofObject(action, "backgroundColor", ArgbEvaluator(), ContextCompat.getColor(requireContext(), R.color.purple_200), ContextCompat.getColor(requireContext(), R.color.teal_200))
//        ag.duration = 1000
//        ag.start()

//        var ag = ObjectAnimator.ofArgb(row, "backgroundColor", Color.BLACK, Color.RED)
//        ag.duration = 2500
//        ag.repeatCount = -1
//        ag.repeatMode = ValueAnimator.REVERSE
//        ag.start()

        return row

    }

    override fun onDestroyView() {
        super.onDestroyView()
        ddr.listeners.remove(ddrListener) // must remove listener to stop null binding
        _binding = null
    }

    fun directionImageId(bgl : BGLReading): Int? {
        var  diff = bgl.calculateDiff()
        if (diff == null) {
            return null
        }
        when {
            diff < -1 -> {
                return R.drawable.ic_down_arrow
            }
            diff < -0.2 -> {
                return R.drawable.ic_downish_arrow
            }
            diff > 1 -> {
                return R.drawable.ic_up_arrow
            }
            diff > 0.2 -> {
                return R.drawable.ic_upish_arrow
            }
        }
        return R.drawable.ic_side_arrow
    }

}