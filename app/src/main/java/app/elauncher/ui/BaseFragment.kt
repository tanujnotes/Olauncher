package app.elauncher.ui

import android.animation.Animator
import android.animation.ValueAnimator
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import app.elauncher.helper.FontManager
import app.elauncher.helper.isEinkDisplay
import app.elauncher.helper.isSystemAnimationsDisabled

open class BaseFragment : Fragment() {

    /**
     * Single place the custom-font typeface gets applied to every fragment's inflated layout -
     * HomeFragment, AppDrawerFragment, SettingsFragment and PagesSettingsFragment all extend this
     * class. No-ops unless the user has picked a custom font (see [FontManager.applyCustomTypeface]).
     *
     * Subclasses call super.onViewCreated() first, so this runs against the freshly inflated XML
     * tree. It deliberately doesn't reach RecyclerView rows or code-built views - those apply the
     * typeface at their own creation time.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        FontManager.applyCustomTypeface(view)
    }

    override fun onCreateAnimator(transit: Int, enter: Boolean, nextAnim: Int): Animator? {
        if (nextAnim != 0 && (requireContext().isSystemAnimationsDisabled() || requireContext().isEinkDisplay()))
            return ValueAnimator.ofFloat(0f, 1f).setDuration(0)
        return null
    }
}
