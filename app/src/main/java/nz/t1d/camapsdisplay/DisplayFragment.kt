package nz.t1d.camapsdisplay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.format.DateUtils
import android.view.*
import android.widget.ImageView
import android.widget.RemoteViews
import android.widget.TextView
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import nz.t1d.camapsdisplay.databinding.FragmentDisplayBinding
import nz.t1d.di.DisplayDataRepository
import java.text.DecimalFormat
import java.time.Instant
import javax.inject.Inject

@AndroidEntryPoint
class DisplayFragment : Fragment() {

    private lateinit var notificationReciever: BroadcastReceiver

    @Inject
    lateinit var readingsRepository: DisplayDataRepository


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
        binding.bglTime.format = ""
        binding.bglTime.start()
        binding.bglTime.setOnChronometerTickListener { chrono ->
            chrono.text = DateUtils.getRelativeTimeSpanString(chrono.base)
        }

        // Previous notification recieved

        var previousND: NotificationData = processView(null)


        // Listen to notifications update the fragment
        notificationReciever = object : BroadcastReceiver() {
            override fun onReceive(contxt: Context?, intent: Intent?) {
                // Get the View of the notifications
                val rv = intent?.extras?.get("view") as RemoteViews
                // Expand it to be an object
                val v = rv.apply(activity!!.applicationContext!!, null)

                // Extract the information from the view
                val nd = processView(v)
                setValues(nd, previousND)
                previousND = nd
            }
        }

        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(
            notificationReciever, IntentFilter("CamAPSFXNotification")
        )


        // Setup the actions on the menu when the fragment is open
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                // Add menu items here
                menuInflater.inflate(R.menu.menu, menu)
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

        return binding.root
    }


    fun setValues(nd: NotificationData, previousND: NotificationData) {
        binding.bglImage.setImageDrawable(nd.image_drawable)
        binding.bglReading.text = nd.reading.toString()
        binding.bglUnits.text = nd.unit
        binding.bglTime.base = nd.time

        // calculate diff
        val dec = DecimalFormat("+#,##0.0;-#")
        println("${nd.time} nd.time ${nd.reading})")
        val readingDiff = nd.reading - previousND.reading
        val fiveMinutes = (nd.time - previousND.time) / (60000.0 * 5.0)
        val diff = readingDiff / fiveMinutes
        binding.bglDiff.text = "${dec.format(diff)} ${nd.unit}"

    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.bglTime.stop()
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(
            notificationReciever
        )
        _binding = null
    }

    data class NotificationData(
        var reading: Float,
        var unit: String,
        var time: Long,
        var image_drawable: Drawable?
    )

    // modified from xdrip
    private fun processView(view: View?): NotificationData {
        // recursivly loop through all children looking for info
        val nd = NotificationData(0.0f, "mmol/L", Instant.now().toEpochMilli(), null)
        if (view != null) {
            getTextViews(nd, view.rootView as ViewGroup)
        }
        return nd
    }


    private fun getTextViews(output: NotificationData, parent: ViewGroup) {
        val children = parent.childCount
        for (i in 0 until children) {
            val view = parent.getChildAt(i)
            if (view.visibility === View.VISIBLE) {
                if (view is TextView) {
                    val text = view.text.toString()
                    if (text.matches("[0-9]+[.,][0-9]+".toRegex())) {
                        output.reading = text.toFloat()
                    }
                } else if (view is ImageView) {
                    val iv = view
                    output.image_drawable = iv.drawable
                } else if (view is ViewGroup) {
                    getTextViews(output, view)
                }
            }
        }
    }
}