package com.raka.fastextractor

import android.os.Bundle
import android.util.SparseArray
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.raka.fastextractor.ui.theme.FastExtractorTheme
import com.raka.fastextractorlib.Loggers
import com.raka.fastextractorlib.VideoMeta
import com.raka.fastextractorlib.YtFile
import com.raka.fastextractorlib.YtbExtractor

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FastExtractorTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Greeting("Android")
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val youtubeUrl = "https://www.youtube.com/watch?v=5ctvDMRse9g&ab_channel=SlayyPoint"
        Loggers.e("YtbExtractor", "---")

//        buttonTest.setOnClickListener {
            Loggers.e("YtbExtractor", "--STARTING_--")
            val ytb = YtbExtractor(this, getString(R.string.yt))
            ytb.onAsyncTaskListener = object : YtbExtractor.OnAsyncTaskListener {
                override fun onExtractionComplete(
                    ytFiles: SparseArray<YtFile>?,
                    videoMeta: VideoMeta?
                ) {
                    if (ytFiles == null) {
                        Loggers.e("YtbExtractor", "video invalid")
                        return
                    }
                    Loggers.e("YtbExtractor", "youtubeUrl = $youtubeUrl")
                    for (i in 0 until ytFiles.size()) {
                        val itag = ytFiles.keyAt(i)
                        val yFile = ytFiles.get(itag)
                        val streamUrl = yFile.url
                        val format = yFile.format
                        Loggers.e("YtbExtractor_", "itag = ${format.itag}, $streamUrl")
                    }
                    Loggers.e("YtbExtractor", "-----------------------")
                }
            }
            ytb.start(youtubeUrl)
//        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    FastExtractorTheme {
        Greeting("Android")
    }
}