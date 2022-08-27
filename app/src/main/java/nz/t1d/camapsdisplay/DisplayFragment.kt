package nz.t1d.camapsdisplay

import android.os.Bundle
import android.text.format.DateUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Chronometer
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.setMargins
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import nz.t1d.camapsdisplay.databinding.FragmentDisplayBinding
import nz.t1d.di.BGLReading
import nz.t1d.di.BasalInsulinChange
import nz.t1d.di.BaseDataClass
import nz.t1d.di.BolusInsulin
import nz.t1d.di.CarbIntake
import nz.t1d.di.DisplayDataRepository
import java.text.NumberFormat
import java.time.ZoneOffset
import javax.inject.Inject

@AndroidEntryPoint
class DisplayFragment : Fragment() {

    @Inject
    lateinit var ddr: DisplayDataRepository
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

        // diasend values


        binding.iobbolustv.text = "${nf1dp.format(ddr.insulinOnBoardBolus)}u"
        binding.iobbasaltv.text = "${nf1dp.format(ddr.insulinOnBoardBasal)}u"
        binding.TIRtv.text = "${nf0dp.format(ddr.timeInRange*100)}%"
        binding.basaltv.text = "${nf2dp.format(ddr.insulinCurrentBasal)}u"
        binding.meanstdtv.text = "${nf1dp.format(ddr.meanBGL)}/${nf1dp.format(ddr.stdBGL)}"

        // Build the list of recent events
        binding.recentEventRows.removeAllViews()
        for( re in ddr.recentEvents) {
            binding.recentEventRows.addView(createRecentEventRow(re))
        }
        binding.recentBglRows.removeAllViews()
        for( re in ddr.bglReadings.take(10)) {
            binding.recentBglRows.addView(createRecentEventRow(re))
        }
    }

    private fun createRecentEventRow(re: BaseDataClass): View {

        val row =  LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val layoutParams = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT
        )
        layoutParams.setMargins(20)
        row.layoutParams = layoutParams

        val lps = { -> val lp = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(10,3,3,3)
            lp
        }
        val image = ImageView(context)
        val text = TextView(context)

        image.layoutParams = RelativeLayout.LayoutParams(50, 50)
        text.layoutParams = lps()

        val dBasal = ResourcesCompat.getDrawable(requireContext().resources, R.drawable.ic_basal, null)
        val dBolus = ResourcesCompat.getDrawable(requireContext().resources, R.drawable.ic_bolus, null)
        val dBGL = ResourcesCompat.getDrawable(requireContext().resources, R.drawable.ic_bgl, null)
        val dCarb = ResourcesCompat.getDrawable(requireContext().resources, R.drawable.ic_carb, null)

        when (re) {
            is BolusInsulin -> {image.setImageDrawable(dBolus); text.text = "${re.value}u (${nf1dp.format(re.valueAfterDecay())}u) bolus -- ${re.minsAgoString()}"}
            is CarbIntake -> {image.setImageDrawable(dCarb); text.text = "${re.value}g carbs -- ${re.minsAgoString()}"}
            is BGLReading -> {image.setImageDrawable(dBGL); text.text = "${re.minsAgoString()} -- ${re.value}mmol/L"}
        }

        row.addView(image)
        row.addView(text)

        return row
//        <LinearLayout
//        android:layout_width="match_parent"
//        android:layout_height="match_parent"
//        android:orientation="horizontal"
//        android:gravity="left"
//        android:layout_margin="5dp"
//        android:layout_gravity="center_vertical">
//
//        <ImageView
//        android:id="@+id/imageView"
//        android:layout_width="wrap_content"
//        android:layout_height="20dp"
//
//        android:layout_weight="0"
//        android:src="@drawable/ic_bolus_24dp" />
//
//        <TextView
//        android:id="@+id/textView"
//        android:layout_width="wrap_content"
//        android:layout_height="wrap_content"
//        android:layout_weight="1"
//        android:textSize="14sp"
//        android:text="" />
//        </LinearLayout>
    }
    override fun onDestroyView() {
        super.onDestroyView()
        binding.bglTime.stop()
        ddr.listeners.remove(ddrListener) // must remove listener to stop null binding
        _binding = null
    }


}