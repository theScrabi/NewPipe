package org.schabi.newpipe.about

/**
 * Class containing information about standard software licenses.
 */
object StandardLicenses {
    @JvmField
    val GPL3 = License("GNU General Public License, Version 3.0", "GPLv3", "gpl_3.html")

    @JvmField
    val APACHE2 = License("Apache License, Version 2.0", "ALv2", "apache2.html")

    @JvmField
    val MPL2 = License("Mozilla Public License, Version 2.0", "MPL 2.0", "mpl2.html")

    @JvmField
    val MIT = License("MIT License", "MIT", "mit.html")

    @JvmField
    val EPL1 = License("Eclipse Public License, Version 1.0", "EPL 1.0", "epl1.html")
}
