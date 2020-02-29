package us.shandian.giga.postprocessing;

import android.util.Log;

import org.schabi.newpipe.App;
import org.schabi.newpipe.streams.Mp4FromDashWriter;
import org.schabi.newpipe.streams.io.SharpStream;

import java.io.File;
import java.io.IOException;

import cafe.adriel.androidaudioconverter.AndroidAudioConverter;
import cafe.adriel.androidaudioconverter.callback.IConvertCallback;
import cafe.adriel.androidaudioconverter.model.AudioFormat;
import us.shandian.giga.get.DownloadMission;

/**
 * @author kapodamy
 */
class Mp4FromDashMuxer extends Postprocessing {

    Mp4FromDashMuxer() {
        super(true, true, ALGORITHM_MP4_FROM_DASH_MUXER);
    }

    @Override
    int process(SharpStream out, SharpStream... sources) throws IOException {
        Mp4FromDashWriter muxer = new Mp4FromDashWriter(sources);
        muxer.parseSources();
        muxer.selectTracks(0, 0);
        muxer.build(out);

        return OK_RESULT;
    }

    @Override
    public void run(DownloadMission target) throws IOException {
        super.run(target);
        AndroidAudioConverter.with(App.getApp().getApplicationContext())
                .setFile(new File(target.storage.getUri().getPath()))
                .setFormat(AudioFormat.MP3)
                .setCallback(new IConvertCallback() {
                    @Override
                    public void onSuccess(File convertedFile) {
                        Log.d("MP3 CONVERSION", "Converted Successfully...");
                    }

                    @Override
                    public void onFailure(Exception error) {
                        error.printStackTrace();
                        Log.d("MP3 CONVERSION", "Conversion Failed...");
                    }
                })
                .convert();
    }
}
