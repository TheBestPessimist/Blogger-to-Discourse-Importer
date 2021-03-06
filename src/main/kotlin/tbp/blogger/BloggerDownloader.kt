package tbp.blogger

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.blogger.Blogger
import com.google.api.services.blogger.BloggerScopes
import com.google.api.services.blogger.model.Post
import io.github.openunirest.http.Unirest
import org.apache.commons.codec.digest.DigestUtils
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.TextNode
import org.jsoup.safety.Whitelist
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Paths

@Suppress("PrivatePropertyName")
class BloggerDownloader(private val blogID: String) {

    private val JSON_FACTORY = JacksonFactory.getDefaultInstance()
    private val HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport()
    private val BLOGGER_CREDENTIALS_FILE = "resources/blogger import-a5fa3807a02e.json"


    fun doDownload(): Blog {
        return getPostsAndComments(blogID)
    }


    private fun getPostsAndComments(blogID: String): Blog {
        val credential: Credential = auth()
        val blogger = Blogger.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential).setApplicationName("penis").build()
        val bBlog = blogger.blogs().get(blogID).execute()        // not really needed. won't delete it tho :P
        val blog = Blog(bBlog.name, mutableListOf())

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

        return blog
    }


    private fun createPost(bPost: Post, blogger: Blogger): tbp.blogger.Post {
        val bComments: MutableList<Comment> = getCommentsForPost(bPost, blogger)

        @Suppress("UnnecessaryVariable")
        val post = with(bPost) {

            val contentAndImgPair = getImagesFromBody(content)
            var cleansedContent = contentAndImgPair.first
            cleansedContent = getRidOfYoutubeEmbeds(cleansedContent)

            val title = handleInvalidTitle(title, cleansedContent)
            val p = Post(
                title,
                cleansedContent,
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
            p.images = contentAndImgPair.second
            p
        }
        return post
    }

    /**
     * Download the images in a post in memory and return a list of those
     * Replace the img tag with a unique image name, which will be used to link to the specific discourse upload
     */
    private fun getImagesFromBody(content: String): Pair<String, List<Image>> {
        val images = mutableListOf<Image>()
        val doc = Jsoup.parse(content)
        doc.select("img[src]").forEach { it ->
            val src = it.attr("src")
            val name = DigestUtils.sha1Hex(src)
            val out = ByteArrayOutputStream()
            val len = Unirest.get(src).asBinary().body.buffered().copyTo(out)

            if (len > 0) {
                images.add(Image(name, out.toByteArray(), src))
                if ("a" == it.parent().tagName()) {
                    it.parent().replaceWith(TextNode("\n$name\n"))
                } else {
                    it.replaceWith(TextNode("\n$name\n"))
                }
            }
        }
        return Pair(doc.body().childNodes().joinToString(""), images.toList())
    }

    /**
     * If the title is empty, return the first phrase in the content, stripped of all html
     */
    @Suppress("ReplaceGetOrSet")
    private fun handleInvalidTitle(title: String?, content: String): String {
        return if (title.isNullOrEmpty()) {
            val cleanContent = Jsoup.clean(
                content,
                "",
                Whitelist.none()
                , Document.OutputSettings().prettyPrint(false)  // this is needed so that newlines remain in the cleaned html.
            )
            cleanContent.split("\n")
                .filter { !it.isEmpty() }
                .get(0)
                .split("""\W""".toRegex())
                .filter { !it.isEmpty() }
                .take(4)
                .joinToString(" ")
        } else {
            title!!
        }
    }

    /**
     * Get a MutableList of dComments for a specific post.
     */
    private fun getCommentsForPost(post: Post, blogger: Blogger): MutableList<Comment> {
        val comments: MutableList<Comment> = mutableListOf()

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


    /**
     * Replace iframe<src> and object > embed <src> with just the src.
     */
    private fun getRidOfYoutubeEmbeds(content: String): String {
        val doc = Jsoup.parse(content)
        val media = doc
            .select("[src]")
            .filter { it.attr("src").contains("youtube", true) }

        media.forEach {
            val src = it
                .attr("src")
                .replace("v/", "watch?v=", true)            // /v/ is for embeds, i need normal, /watch?v= links
                .replace(
                    "embed/",
                    "watch?v=",
                    true
                )        // /embed/ is for iframes, ^^^^^^^^^ (one might consider why the names are reversed)
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


    private fun auth(): Credential {
        val googleCredential = GoogleCredential
            .fromStream(Files.newInputStream(Paths.get(BLOGGER_CREDENTIALS_FILE)))
            .createScoped(BloggerScopes.all())
        googleCredential.refreshToken()

        return googleCredential
    }


}
