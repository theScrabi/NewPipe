package org.schabi.newpipe.about

import android.os.Bundle
import android.view.ContextMenu
import android.view.ContextMenu.ContextMenuInfo
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import io.reactivex.rxjava3.disposables.CompositeDisposable
import org.schabi.newpipe.R
import org.schabi.newpipe.about.LicenseFragmentHelper.showLicense
import org.schabi.newpipe.databinding.FragmentLicensesBinding
import org.schabi.newpipe.databinding.ItemSoftwareComponentBinding
import org.schabi.newpipe.util.ShareUtils
import java.util.Arrays
import java.util.Objects

/**
 * Fragment containing the software licenses.
 */
class LicenseFragment : Fragment() {
    private lateinit var softwareComponents: Array<SoftwareComponent>
    private var componentForContextMenu: SoftwareComponent? = null
    private var activeLicense: License? = null
    private val compositeDisposable = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        softwareComponents =
            arguments?.getParcelableArray(ARG_COMPONENTS) as Array<SoftwareComponent>
        if (savedInstanceState != null) {
            val license = savedInstanceState.getSerializable(LICENSE_KEY)
            if (license != null) {
                activeLicense = license as License?
            }
        }
        // Sort components by name
        Arrays.sort(softwareComponents, Comparator.comparing(SoftwareComponent::name))
    }

    override fun onDestroy() {
        compositeDisposable.dispose()
        super.onDestroy()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentLicensesBinding.inflate(inflater, container, false)
        binding.licensesAppReadLicense.setOnClickListener {
            activeLicense = StandardLicenses.GPL3
            compositeDisposable.add(
                showLicense(activity, StandardLicenses.GPL3)
            )
        }
        for (component in softwareComponents) {
            val componentBinding = ItemSoftwareComponentBinding
                .inflate(inflater, container, false)
            componentBinding.name.text = component.name
            componentBinding.copyright.text = getString(
                R.string.copyright,
                component.years,
                component.copyrightOwner,
                component.license.abbreviation
            )
            val root: View = componentBinding.root
            root.tag = component
            root.setOnClickListener {
                activeLicense = component.license
                compositeDisposable.add(
                    showLicense(activity, component.license)
                )
            }
            binding.licensesSoftwareComponents.addView(root)
            registerForContextMenu(root)
        }
        if (activeLicense != null) {
            compositeDisposable.add(
                showLicense(activity, activeLicense!!)
            )
        }
        return binding.root
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenuInfo?) {
        val inflater = requireActivity().menuInflater
        val component = v.tag as SoftwareComponent
        menu.setHeaderTitle(component.name)
        inflater.inflate(R.menu.software_component, menu)
        super.onCreateContextMenu(menu, v, menuInfo)
        componentForContextMenu = component
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        // item.getMenuInfo() is null so we use the tag of the view
        val component = componentForContextMenu ?: return false
        when (item.itemId) {
            R.id.menu_software_website -> {
                ShareUtils.openUrlInBrowser(activity, component.link)
                return true
            }
            R.id.menu_software_show_license -> compositeDisposable.add(
                showLicense(activity, component.license)
            )
        }
        return false
    }

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        if (activeLicense != null) {
            savedInstanceState.putSerializable(LICENSE_KEY, activeLicense)
        }
    }

    companion object {
        private const val ARG_COMPONENTS = "components"
        private const val LICENSE_KEY = "ACTIVE_LICENSE"
        fun newInstance(softwareComponents: Array<SoftwareComponent>): LicenseFragment {
            val fragment = LicenseFragment()
            fragment.arguments =
                bundleOf(ARG_COMPONENTS to Objects.requireNonNull(softwareComponents))
            return fragment
        }
    }
}
