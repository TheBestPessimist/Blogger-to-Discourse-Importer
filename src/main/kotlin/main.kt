import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.blogger.Blogger
import com.google.api.services.blogger.BloggerScopes
import com.google.api.services.blogger.model.Post
import tbp.blogger.reader.Blog
import tbp.blogger.reader.Comment
import tbp.blogger.toLocalDateTime
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

private val A_SHADOW_OF_THE_DAY = "1653548642480190066"
private val THE_MIGHTY_NAHSUCS_SONG_OF_THE_DAY = "4862558016181549125"
private val BLOGGER_CREDENTIALS_FILE = "resources/blogger import-a5fa3807a02e.json"

private val JSON_FACTORY = JacksonFactory.getDefaultInstance()
private val HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport()

private lateinit var dBlog: Blog // from DiscourseBlog :^)

fun main(args: Array<String>) {
    initProxy()

    val b = THE_MIGHTY_NAHSUCS_SONG_OF_THE_DAY
//    val b = A_SHADOW_OF_THE_DAY;

    dBlog = Blog(b, mutableListOf())

    getPostsAndComments(b)
}

fun getPostsAndComments(blogID: String) {
    val credential: Credential = auth()
    val blogger = Blogger.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential).setApplicationName("penis").build()
    val blog = blogger.blogs().get(blogID).execute()

    // first create a request
    val postListRequest = blogger.posts().list(blog.id)
    postListRequest.maxResults = blog.posts.totalItems.toLong()

    // that request is executed and returns a JSON object which contains
    // the needed items (posts in this case)
    // and a token used for pagination (making a new request)
    var postList = postListRequest.execute()
    var posts = postList.items

    while (posts != null && posts.isNotEmpty()) {
        for (post in posts) {
//            println("post: ${post.title}") // + if (post.replies.totalItems != 0.toLong()) "======================================= comments: ${post}" else "" )
            val comments: MutableList<tbp.blogger.reader.Comment> = getCommentsForPost(post, blogger)

            val dpost = tbp.blogger.reader.Post(post.title, post.content, comments, post.updated.toLocalDateTime())
            println(dpost)
        }


        // if there's no more "next pages" the iteration is finished
        if (postList.nextPageToken.isNullOrEmpty()) {
            break;
        }

        // pagination => new request
        postListRequest.setPageToken(postList.nextPageToken)
        postList = postListRequest.execute()
        posts = postList.items
    }
}

/**
 * Get a MutableList of dComments for a specific post.
 */
fun getCommentsForPost(post: Post, blogger: Blogger): MutableList<tbp.blogger.reader.Comment> {
    val comments: MutableList<tbp.blogger.reader.Comment> = mutableListOf()

    if (post.replies.totalItems != 0.toLong()) {
        val commentList = blogger.comments().list(post.blog.id, post.id).setMaxResults(500).execute().items

        for (cc in commentList) {
            with(cc) {
                val dComment = Comment(author.displayName, content, updated.toLocalDateTime())
                comments.add(dComment)
            }
        }
    }

    return comments
}


fun auth(): Credential {

    val googleCredential = GoogleCredential
            .fromStream(Files.newInputStream(Paths.get(BLOGGER_CREDENTIALS_FILE)))
            .createScoped(BloggerScopes.all())
    googleCredential.refreshToken()

    return googleCredential
}


/**
 * Proxy is needed only for work pc.
 */
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
