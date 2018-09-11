package tbp

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.blogger.Blogger
import com.google.api.services.blogger.BloggerScopes
import com.google.api.services.blogger.model.Post
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

import org.jsoup.nodes.TextNode
import org.jsoup.safety.Whitelist
import tbp.blogger.reader.Blog
import tbp.blogger.reader.Comment
import tbp.blogger.toLocalDateTime
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

@Suppress("unused")
private const val A_SHADOW_OF_THE_DAY = "1653548642480190066"
private const val THE_MIGHTY_NAHSUCS_SONG_OF_THE_DAY = "4862558016181549125"
private const val BLOGGER_CREDENTIALS_FILE = "resources/blogger import-a5fa3807a02e.json"

private val JSON_FACTORY = JacksonFactory.getDefaultInstance()
private val HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport()

public lateinit var blog: Blog // from DiscourseBlog :^)

fun main(args: Array<String>) {
    doWork()
}

fun doWork() {
//    val blogID = tbp.A_SHADOW_OF_THE_DAY;
    val blogID = THE_MIGHTY_NAHSUCS_SONG_OF_THE_DAY

//    initProxy()

    getPostsAndComments(blogID)
}

fun getPostsAndComments(blogID: String) {
    val credential: Credential = auth()
    val blogger = Blogger.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential).setApplicationName("penis").build()
    val bBlog = blogger.blogs().get(blogID).execute()        // not really needed. won't delete it tho :P
    blog = Blog(bBlog.name, mutableListOf())

    // first create a request
    val bPostListRequest = blogger.posts().list(bBlog.id)
    bPostListRequest.maxResults = bBlog.posts.totalItems.toLong()

    // that request is executed and returns a JSON object which contains
    // the needed items (posts in this case)
    // and a token used for pagination (making a new request)
    var bPostList = bPostListRequest.execute()
    var bPosts = bPostList.items


    while (bPosts != null && bPosts.isNotEmpty()) {
        bPosts.forEach { bPost ->
            //                        println("bPost: ${bPost.content}\n")
            val post = createPost(bPost, blogger)
            blog.posts.add(post)
        }


        // if there's no more "next pages" the iteration is finished
        if (bPostList.nextPageToken.isNullOrEmpty()) {
            break
        }

        // pagination => new request
        bPostListRequest.pageToken = bPostList.nextPageToken
        bPostList = bPostListRequest.execute()
        bPosts = bPostList.items
    }
}

fun createPost(bPost: Post, blogger: Blogger): tbp.blogger.reader.Post {
    val bComments: MutableList<tbp.blogger.reader.Comment> = getCommentsForPost(bPost, blogger)

    @Suppress("UnnecessaryVariable")
    val post = with(bPost) {
        tbp.blogger.reader.Post(
            title,
            getRidOfYoutubeEmbeds(content),
            bComments,
            updated.toLocalDateTime(),
            /**
             * explanation of following:
             * the labels (tags of a bPost) can be null.
             * hence i use "?." to check for and return null and IGNORE a possible NPE when casting to mutable set
             * and then i use "?:" to return an empty mutableSet if anything on the left is null.
             *
             * java code:
             * void getLabels(labels){
             *      if(labels == null) {
             *           return mutableSetOf<String>()
             *      } else {
             *           val a = mutableSetOf<String>()
             *           a.addAll(labels)            // @dst this returns a boolean so i cant just do "return mutableSetOf<String>().addAll(labels)" :D
             *           return a
             *      }
             * }
             */
            labels?.toMutableSet() ?: mutableSetOf(),
            url
        )
    }
    return post
}

/**
 * Replace iframe[src] and object > embed [src] with just the src.
 */
fun getRidOfYoutubeEmbeds(content: String): String {
    val doc = Jsoup.parse(content)
    val media = doc
        .select("[src]")
        .filter { it.attr("src").contains("youtube", true) }

    media.forEach {
        val src = it
            .attr("src")
            .replace("v/", "watch?v=", true)            // /v/ is for embeds, i need normal, /watch?v= links
            .replace("embed/", "watch?v=", true)        // /embed/ is for iframes, ^^^^^^^^^ (one might consider why the names are reversed)
            .replace("http://", "https://", true)       // it's 2018. there's no any other fucking way!
        if (it.tagName() == "iframe") {
            it.replaceWith(TextNode(" $src "))
        } else if (it.tagName() == "embed") {
            it.parent().replaceWith(TextNode(" $src "))
        }
    }
    val r = doc.body().childNodes().joinToString("")
        .replace("https://youtube", "\nhttps://youtube", true)
        .replace("https://www.youtube", "\nhttps://www.youtube", true)
        .replace("<br>", "", true)
        .replace("<br/>", "", true)
        /**
         *  add 2 newlines after each youtube link. This should fix youtube oneboxing issues.
         *
         *  You probably noticed that this replace has only 1 argument, and a lambda after it, just like that. (even though all replace methods have at least 2 arguments!)
         *  It's explained in the docs: https://kotlinlang.org/docs/reference/lambdas.html#passing-a-lambda-to-the-last-parameter
         *
         *  You have seen this used before, for example when doing `collection.filter {}.map {}` etc.
         */
        .replace("""youtube\.com\S+""".toRegex(setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))) {
            "${it.value}\n\n"
        }

    return Jsoup.clean(
        r,
        "",
        Whitelist.basicWithImages()
//            .addTags("div")
//            .removeAttributes("div", "color")
//            .addAttributes("div", "text-align")
        , Document.OutputSettings().prettyPrint(false)  // this is needed so that newlines remain in the cleaned html.
    )
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
