package tbp.discourse

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import tbp.blogger.Blog
import tbp.blogger.Comment
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration
import java.time.Instant
import java.util.*


class DiscourseUploader(private val blog: Blog, discourseUrl: String) {
    @Suppress("PrivatePropertyName")
    private val DISCOURSE_API_CREDENTIALS = "resources/discourse.api.key.properties"

    private val categoryName = blog.name
    private val discourse: DiscourseClient

    init {
        val credentials = loadApiCredentials()
        discourse = DiscourseClient(credentials["api_key"]!!, credentials["api_username"]!!, discourseUrl)
    }

    fun doUpload() {
        cleanup()

        // create the new category
        discourse.createNewCategory(categoryName, "red", "FFFFFF", true).body
        val categoryId = discourse.searchCategoryByName(categoryName)

        uploadPosts(categoryId)
    }

    private fun uploadPosts(categoryId: Int) {
        println("begin upload")
        val now = Instant.now()

        blog.posts.forEach {
            var theContent = it.content

            it.images.forEach { img ->
                val uploadedUrl = discourse.uploadFile(ByteArrayInputStream(img.raw), img.name)
                theContent = theContent.replace(img.name, "\n$uploadedUrl\n")
            }

            val res = discourse.createNewTopic(it.title, theContent, categoryId, it.date)

            with(res) {
                if (200 != status) {
                    val s = "${Instant.now()}: $status $statusText >${it.title}<\n$body\n${it.bloggerURL}" + "==".repeat(30)
                    println(s)
                }
            }

            val mapper = jacksonObjectMapper()
            val topicId = mapper.readTree(res.body.toString())["topic_id"].asInt()
            it.comments.sortedBy(Comment::date).forEach { c ->
                val rawContent = "Comment\nAuthor: " + c.author + "\n" + "Message: " + c.content
                discourse.createReplyToTopic(topicId, rawContent, c.date)
            }

            // tags
            discourse.createReplyToTopic(topicId, it.tags.joinToString(", ", "Tags: >>", "<<"), it.date)
            // original blogger post URL
            discourse.createReplyToTopic(topicId, it.bloggerURL, it.date)
        }

        println("upload finished. duration: " + Duration.between(now, Instant.now()))
    }

    private fun cleanup() {
        println("begin cleanup")
        val now = Instant.now()
        val categoryId = discourse.searchCategoryByName(categoryName)
        val res = discourse.getAllTopicsForCategory(categoryId)
        discourse.deleteCategoryAndTopics(categoryId, res)
        println("finished cleanup in " + Duration.between(now, Instant.now()))
    }


    private fun loadApiCredentials(): Map<String, String> {
        val props = Properties()
        props.load(Files.newBufferedReader(Paths.get(DISCOURSE_API_CREDENTIALS)))
        return mapOf(
            "api_key" to props.getProperty("api_key"),
            "api_username" to props.getProperty("api_username")
        )
    }
}
