package tbp

import tbp.blogger.BloggerDownloader
import tbp.discourse.DiscourseUploader
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

@Suppress("unused")
private const val SHADOW = "1653548642480190066"
private const val SONG_OF_THE_DAY = "4862558016181549125"

fun main(args: Array<String>) {
    //    initProxy()

//    val blogID = SHADOW
    val blogID = SONG_OF_THE_DAY

    var blog = BloggerDownloader(blogID).doDownload()

//    testing filter for adding only the possibly problematic posts
//    blog = blog.copy(
//        posts = blog.posts.filter {
//            it.title.contains("weekend", true)
//        }.toMutableList()
//    )

    DiscourseUploader(blog, "https://chat.tbp.land").doUpload()
}


/**
 * Proxy is needed only for work pc.
 */
@Suppress("unused")
fun initProxy() {
    val hostName = System.getenv("COMPUTERNAME") ?: System.getenv("hostname") // windows and unix?

    if ("TIMW0150" == hostName) {
        val props = Properties()
        props.load(Files.newBufferedReader(Paths.get("resources/proxy.properties")))

        System.setProperty("http.proxyHost", props.getProperty("proxyHost"))
        System.setProperty("http.proxyPort", props.getProperty("proxyPort"))
        System.setProperty("http.proxyUser", props.getProperty("proxyUser"))
        System.setProperty("http.proxyPassword", props.getProperty("proxyPassword"))
        System.setProperty("http.nonProxyHosts", props.getProperty("noproxyHosts"))

        System.setProperty("https.proxyHost", props.getProperty("proxyHost"))
        System.setProperty("https.proxyPort", props.getProperty("proxyPort"))
        System.setProperty("https.nonProxyHosts", props.getProperty("noproxyHosts"))
    }
}
