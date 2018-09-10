package tbp

import io.github.openunirest.http.HttpResponse
import io.github.openunirest.http.JsonNode
import tbp.discourse.client.DiscourseClient
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDateTime
import java.util.*

private lateinit var API_KEY: String
private lateinit var API_USERNAME: String

private lateinit var discourse: DiscourseClient

private var res: Any = Any()  // just a dump (response) variable. Any in kotlin = Object in java

fun main(args: Array<String>) {
    loadApiCredentials()
    discourse = DiscourseClient(API_KEY, API_USERNAME, "https://chat.tbp.land/")

    val categoryName = "penis"
    val topicTitle = "new topic fdafdsafda"
    var categoryId = discourse.searchCategoryByName(categoryName)

    // cleanup
    res = discourse.getAllTopicsForCategory(categoryId)
    res = discourse.deleteCategoryAndTopics(categoryId, res as List<Int>).body
    res = discourse.getAllTopicsForCategory(categoryId)                          // if it doesn't work the first time...
    res = discourse.deleteCategoryAndTopics(categoryId, res as List<Int>).body
    res = discourse.createNewCategory(categoryName, "red", "FFFFFF").body
    categoryId = discourse.searchCategoryByName(categoryName)

    ////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////


    res = discourse.createNewTopic(
        topicTitle,
        "some content 2011-04-12T16:23:46",
        categoryId,
        LocalDateTime.parse("2011-04-12T16:23:46")
    ).body
    println(res)
}


private fun loadApiCredentials() {
    val props = Properties()
    props.load(Files.newBufferedReader(Paths.get("resources/discourse.api.key.properties")))
    API_KEY = props.getProperty("api_key")
    API_USERNAME = props.getProperty("api_username")
}


fun HttpResponse<JsonNode>.dbg(): String {
    return ">\n$headers\n$status: $statusText\n$parsingError\n$body\n<"
}
