package tbp.discourse

import tbp.blogger.Blog
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Instant
import java.util.*
import java.util.concurrent.TimeUnit


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
        blog.posts
//        .filter { it.title.contains("oildale", true) }
//        .filter { it.title.contains("bonfires", true) }
            .forEach {
                val res = discourse.createNewTopic(
                    it.title,
                    it.content,
                    categoryId,
                    it.date
                )

                with(res) {
                    if (200 != status) {
                        var s = "${Instant.now()}: $status $statusText >${it.title}<"
                        s += " $body ===${it.content}==="
                        println(s)
                    }
                }
                TimeUnit.MILLISECONDS.sleep(150)
            }

    }

    private fun cleanup() {
        val categoryId = discourse.searchCategoryByName(categoryName)
        var res = discourse.getAllTopicsForCategory(categoryId)
        discourse.deleteCategoryAndTopics(categoryId, res)
        res = discourse.getAllTopicsForCategory(categoryId)                          // if it doesn't work the first time...
        discourse.deleteCategoryAndTopics(categoryId, res)
        println("finished cleanup")
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
