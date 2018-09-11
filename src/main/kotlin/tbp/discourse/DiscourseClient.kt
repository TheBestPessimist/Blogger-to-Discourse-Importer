package tbp.discourse

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.treeToValue
import io.github.openunirest.http.HttpResponse
import io.github.openunirest.http.JsonNode
import io.github.openunirest.http.Unirest
import io.github.openunirest.request.GetRequest
import io.github.openunirest.request.HttpRequestWithBody
import java.net.URI
import java.time.LocalDateTime

@Suppress("unused", "MemberVisibilityCanBePrivate")
class DiscourseClient(apiKey: String, apiUsername: String, baseUrl: String) {

    private val rb: DiscourseRequestBuilder =
        DiscourseRequestBuilder(apiKey, apiUsername, baseUrl)


    ////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    //              topic

    fun getLatestTopics(): HttpResponse<JsonNode> {
        val link = "/latest.json"
        return rb.getRequest(link).asJson()
    }


    fun getTopic(id: Int): HttpResponse<JsonNode> {
        return rb.getRequest("/t/$id.json").asJson()
    }

    fun createNewTopic(title: String, rawContent: String, categoryId: Int, createdAt: LocalDateTime): HttpResponse<JsonNode> {
        val req = rb.postRequest("/posts.json")
        /**
         * contrary to https://docs.discourse.org/#
         * it seems that you don't have to send the json body, but actual form data!
         *
         * hence:
         * - query string doesnt work
         *      (ofc that makes sense because a a web url is limited in length and rawContent of
         *      a post isnt)
         *
         * - sending a json body doesn't work!
         *
         * - the only thing that works is sending a form.
         */


//        req.queryString("title", title)   // not working

//        val json = JSONObject().put("title", title).put("raw", rawContent).put("category", categoryId).put("created_at", createdAt)
//        req.body(json.toString())         // not working either

        req.fields(
            mapOf(
                "title" to title,
                "category" to categoryId,
                "created_at" to createdAt,
                "raw" to rawContent
            )
        )

        return req.asJson()
    }


    fun deleteTopic(id: Int): HttpResponse<JsonNode> {
        val req = rb.deleteRequest("/t/$id.json")
        val json = req.asJson()

        return json
    }


    ////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    //              category

    fun createNewCategory(name: String, color: String, textColor: String, adminOnly: Boolean): HttpResponse<JsonNode> {
        val link = "/categories.json"
        val req = rb.postRequest(link)

        val formData: MutableMap<String, Any> = mutableMapOf(
            "name" to name,
            "color" to color,
            "text_color" to textColor
        )
        if (adminOnly) {
            formData["permissions[admins]"] = 1
        }
        req.fields(formData)

        return req.asJson()
    }

    /**
     * Delete a category.
     * Before calling this method you should make sure than all topics except the default one are already deleted form the category!
     */
    fun deleteCategory(id: Int): HttpResponse<JsonNode> {
        /**
         * i would like here to be able to use
         * req.routeParam()
         * instead of string interpolation
         * but unfortunately URI.create() doesn't like the {}.
         *
         * One way to fix this is by passing a list of pars of strings to deleteRequest
         * along with the link and iterate over the list and fill the params
         *
         * and only then create a new Unirest of my URI-correct link.
         *
         * Do you think it's too much work just for this?
         *
         * ===
         *
         * Why may i want to do that? For sanitizing the user input i guess.
         */
        val link = "/categories/$id.json"
        val req = rb.deleteRequest(link)

        return req.asJson()
    }

    /**
     * Fully delete a category along with it's topics.
     *
     * The default topic ("About the <topicName> category") cannot be deleted,
     * but the category can be deleted when **only** that topic exists.
     *
     * The "not deletable" exceptions are silently ignored!
     */
    fun deleteCategoryAndTopics(categoryID: Int, topicIDs: List<Int>): HttpResponse<JsonNode> {
        for (id in topicIDs) {
            deleteTopic(id)
        }
        return deleteCategory(categoryID)
    }

    fun getAllTopicsForCategory(id: Int): List<Int> {
        val req = rb.getRequest("/c/$id.json")
            val json = req.asJson()

            if (200 != json.status) {
                return listOf()
            }

            val mapper = jacksonObjectMapper()
        return mapper.readTree(json.body?.toString())["topic_list"]["topics"]
            .map { it["id"].asInt() }
    }


    ////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    //              search


    /**
     * Return the category ID if exists -1 otherwise
     */
    fun searchCategoryByName(name: String): Int {
        val link = "/categories.json"
        val req = rb.getRequest(link)

        val mapper = jacksonObjectMapper()
        val treeToValue =
            mapper.treeToValue<Array<DiscourseCategory>>(mapper.readTree(req.asJson().body.toString())["category_list"]["categories"])

        return treeToValue.firstOrNull { name.equals(it.name, true) }?.id ?: -1
    }


    fun searchTopicByTitle(title: String): Int {
        val link = "/search.json"
        val req = rb.getRequest(link)
        req.queryString("q", title)
        val json = req.asJson()

        val mapper = jacksonObjectMapper()
        return mapper.readTree(json.body.toString())["topics"]
            .firstOrNull { title.equals(it["title"].asText(), true) }
            ?.get("id")?.asInt() ?: -1
    }
}

@Suppress("MemberVisibilityCanBePrivate")
class DiscourseRequestBuilder(val apiKey: String, val apiUsername: String, val baseUrl: String) {
    /**
     * Generic POST builder
     */
    fun postRequest(link: String): HttpRequestWithBody {
        val request = Unirest.post(URI.create("$baseUrl/$link").normalize().toString())
        request.queryString("api_key", apiKey)
            .queryString("api_username", apiUsername)
            .header("Content-Type", "multipart/form-data")
            .header("Accept", "application/json")
        return request
    }

    /**
     * Generic DELETE builder
     */
    fun deleteRequest(link: String): HttpRequestWithBody {
        val request = Unirest.delete(URI.create("$baseUrl/$link").normalize().toString())
        request.queryString("api_key", apiKey)
        request.queryString("api_username", apiUsername)
        return request
    }

    /**
     * Generic GET builder
     */
    fun getRequest(link: String): GetRequest {
        val request = Unirest.get(URI.create("$baseUrl/$link").normalize().toString())
        request.queryString("api_key", apiKey)
        request.queryString("api_username", apiUsername)
        return request
    }

    @Suppress("unused")
    fun HttpResponse<JsonNode>.dbg(): String {
        return ">\n$headers\n$status: $statusText\n$parsingError\n$body\n<"
    }

}
