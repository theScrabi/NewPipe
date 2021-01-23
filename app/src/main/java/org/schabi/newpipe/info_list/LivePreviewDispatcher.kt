package org.schabi.newpipe.info_list

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.TransitionDrawable
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.ImageView
import androidx.preference.PreferenceManager
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.functions.BiFunction
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.util.RxStreamFrames
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit

class LivePreviewDispatcher(context: Context) {

    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val preferenceKey = context.getString(R.string.show_live_preview_key)

    private var activePreview: Disposable? = null
    private var currentView: WeakReference<ImageView>? = null

    fun show(target: ImageView, stream: StreamInfoItem) {
        activePreview?.dispose()
        if (!preferences.getBoolean(preferenceKey, true)) {
            return
        }
        val last = target.drawable
        val res = target.resources
        target.startAnimation(createLoadingAnimation())
        activePreview = Observable.zip(
            RxStreamFrames.from(stream)
                .buffer(3)
                .map { x -> x.first() },
            Observable.interval(500, TimeUnit.MILLISECONDS),
            BiFunction<Bitmap, Long, Bitmap> { bmp, _ -> bmp }
        ).subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .map { x -> BitmapDrawable(res, x) }
            .doOnSubscribe {
                currentView = WeakReference(target)
            }.doOnNext {
                if (target.animation != null) {
                    target.clearAnimation()
                }
            }.doFinally {
                target.setImageSmooth(last)
                currentView = null
            }.subscribe(
                { bitmap ->
                    target.setImageSmooth(bitmap)
                },
                { throwable ->
                    throwable.printStackTrace()
                }
            )
    }

    fun cancel(view: ImageView? = null) {
        if (view != null && currentView?.get() != view) {
            return
        }
        activePreview?.dispose()
        activePreview = null
        currentView = null
    }

    private fun createLoadingAnimation(): Animation {
        val res = AlphaAnimation(1f, 0.6f)
        res.duration = 500L
        res.repeatMode = Animation.REVERSE
        res.repeatCount = Animation.INFINITE
        res.interpolator = AccelerateDecelerateInterpolator()
        return res
    }

    private fun ImageView.setImageSmooth(new: Drawable) {
        val old = drawable ?: ColorDrawable(Color.TRANSPARENT)
        val d = TransitionDrawable(arrayOf(old, new))
        d.isCrossFadeEnabled = false
        d.startTransition(200)
        setImageDrawable(d)
    }
}
