package tbp

import io.github.openunirest.http.HttpResponse
import io.github.openunirest.http.JsonNode
import tbp.discourse.client.DiscourseClient
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Instant
import java.util.*
import java.util.concurrent.TimeUnit

private lateinit var API_KEY: String
private lateinit var API_USERNAME: String

private lateinit var discourse: DiscourseClient

private var res: Any = Any()  // just a dump (response) variable. Any in kotlin = Object in java

@Suppress("UNCHECKED_CAST")
fun main(args: Array<String>) {
    loadApiCredentials()
    discourse = DiscourseClient(API_KEY, API_USERNAME, "https://chat.tbp.land/")

    val categoryName = "penis"
    var categoryId = discourse.searchCategoryByName(categoryName)

    // cleanup
    res = discourse.getAllTopicsForCategory(categoryId)
    res = discourse.deleteCategoryAndTopics(categoryId, res as List<Int>).body
    res = discourse.getAllTopicsForCategory(categoryId)                          // if it doesn't work the first time...
    res = discourse.deleteCategoryAndTopics(categoryId, res as List<Int>).body
    res = discourse.createNewCategory(categoryName, "red", "FFFFFF", true).body
    categoryId = discourse.searchCategoryByName(categoryName)

    ////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////


    doWork()

    blog.posts
//        .filter { it.title.contains("oildale", true) }
//        .filter { it.title.contains("bonfires", true) }
        .forEach {
            res = discourse.createNewTopic(
                it.title,
                it.content,
                categoryId,
                it.date
            )

            with(res as HttpResponse<*>) {
                var s = "${Instant.now()} $status $statusText"
                if (200 != status) {
                    s += " $body"
                }
                println(s)
            }
            TimeUnit.MILLISECONDS.sleep(150)
        }
}


private fun loadApiCredentials() {
    val props = Properties()
    props.load(Files.newBufferedReader(Paths.get("resources/discourse.api.key.properties")))
    API_KEY = props.getProperty("api_key")
    API_USERNAME = props.getProperty("api_username")
}


@Suppress("unused")
fun HttpResponse<JsonNode>.dbg(): String {
    return ">\n$headers\n$status: $statusText\n$parsingError\n$body\n<"
}
