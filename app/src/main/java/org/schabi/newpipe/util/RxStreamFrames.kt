package org.schabi.newpipe.util

import android.graphics.Bitmap
import com.nostra13.universalimageloader.core.ImageLoader
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.ObservableEmitter
import io.reactivex.rxjava3.core.ObservableOnSubscribe
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.stream.StreamInfoItem

class RxStreamFrames(private val serviceId: Int, private val url: String) : ObservableOnSubscribe<Bitmap> {

    override fun subscribe(emitter: ObservableEmitter<Bitmap>) {
        try {
            val extractor = NewPipe.getService(serviceId).getStreamExtractor(url)
            extractor.fetchPage()
            val frames = extractor.frames
            val frameset = frames.maxByOrNull { it.frameHeight }
            if (frameset != null) {
                for (url in frameset.urls) {
                    if (emitter.isDisposed) {
                        return
                    }
                    ImageLoader.getInstance().loadImageSync(url).use { bmp ->
                        val xCount = bmp.width / frameset.frameWidth
                        val yCount = bmp.height / frameset.frameHeight
                        for (x in 0 until xCount) {
                            for (y in 0 until yCount) {
                                emitter.onNext(
                                    Bitmap.createBitmap(
                                        bmp,
                                        x * frameset.frameWidth,
                                        y * frameset.frameHeight,
                                        frameset.frameWidth,
                                        frameset.frameHeight
                                    )
                                )
                                if (emitter.isDisposed) {
                                    return
                                }
                            }
                        }
                    }
                }
            }
            emitter.onComplete()
        } catch (t: Throwable) {
            emitter.onError(t)
        }
    }

    inline fun <T> Bitmap.use(block: (Bitmap) -> T) = try {
        block(this)
    } finally {
        recycle()
    }

    companion object {

        @JvmStatic
        fun from(stream: StreamInfoItem): Observable<Bitmap> = Observable.create(
            RxStreamFrames(stream.serviceId, stream.url)
        )

        @JvmStatic
        fun from(entity: StreamEntity): Observable<Bitmap> = Observable.create(
            RxStreamFrames(entity.serviceId, entity.url)
        )
    }
}
