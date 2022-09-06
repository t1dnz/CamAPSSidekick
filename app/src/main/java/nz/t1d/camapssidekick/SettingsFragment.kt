package nz.t1d.camapssidekick

import android.os.Bundle
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsFragment : PreferenceFragmentCompat() {


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        preferenceManager.sharedPreferences?.all?.keys?.map { key ->
            val pref = findPreference<Preference>(key)
            if (pref != null && pref is EditTextPreference) {
                pref.setOnBindEditTextListener { text -> this.onBindEditText(key, text)}
            }
        }

        // The usage of an interface lets you inject your own implementation
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
                        findNavController().navigate(R.id.action_navSettingsFragment_to_navDisplayFragment)
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)

    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
    }

    fun onBindEditText(key: String, editText: EditText) {
        when(key) {
            "diasend_password" -> {  editText.inputType = InputType.TYPE_TEXT_VARIATION_PASSWORD; editText.transformationMethod = PasswordTransformationMethod.getInstance() }
            "diasend_username" -> {editText.inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS}
            "insulin_onset", "insulin_peak", "insulin_duration" -> {editText.inputType = InputType.TYPE_CLASS_NUMBER}
            else -> println("unhandled settings ${key}")
        }
    }
}