package tbp.discourse.client

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
class DiscourseClient(val apiKey: String, val apiUsername: String, val baseUrl: String) {

    fun getLatest(): HttpResponse<JsonNode> {
        val link = "/latest.json"
        return getRequest(link).asJson()
    }

    fun createNewCategory(name: String, color: String, textColor: String): HttpResponse<JsonNode> {
        val link = "/categories.json"
        val req = postRequest(link)
        req.queryString("name", name)
        req.queryString("color", color)
        req.queryString("text_color", textColor)

        return req.asJson()
    }

    fun searchTopicByTitle(title: String): Int {
        val link = "/search.json"
        val req = getRequest(link)
        req.queryString("q", title)
        val json = req.asJson()

        val mapper = jacksonObjectMapper()
        return mapper.readTree(json.body.toString())["topics"]
            .firstOrNull { title.equals(it["title"].asText(), true) }
            ?.get("id")?.asInt() ?: -1
    }

    fun deleteTopic(id: Int): HttpResponse<JsonNode> {
        val req = deleteRequest("/t/$id.json")
        val json = req.asJson()

        return json;
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
        val req = deleteRequest(link)

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

    /**
     * Return the category ID if exists -1 otherwise
     */
    fun searchCategoryByName(name: String): Int {
        val link = "/categories.json"
        val req = getRequest(link)

        val mapper = jacksonObjectMapper()
        val treeToValue =
            mapper.treeToValue<Array<DiscourseCategory>>(mapper.readTree(req.asJson().body?.toString())["category_list"]["categories"])

        return treeToValue.firstOrNull { name.equals(it.name, true) }?.id ?: -1
    }


    /**
     * Generic POST builder
     */
    private fun postRequest(link: String): HttpRequestWithBody {
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
    private fun deleteRequest(link: String): HttpRequestWithBody {
        val request = Unirest.delete(URI.create("$baseUrl/$link").normalize().toString())
        request.queryString("api_key", apiKey)
        request.queryString("api_username", apiUsername)
        return request
    }


    /**
     * Generic GET builder
     */
    private fun getRequest(link: String): GetRequest {
        val request = Unirest.get(URI.create("$baseUrl/$link").normalize().toString())
        request.queryString("api_key", apiKey)
        request.queryString("api_username", apiUsername)
        return request
    }

    fun createNewTopic(title: String, rawContent: String, categoryId: Int, createdAt: LocalDateTime): HttpResponse<JsonNode> {
        val req = postRequest("/posts.json")
        req.queryString("title", title)
        req.queryString("raw", rawContent)
        req.queryString("category", categoryId)
        req.queryString("created_at", createdAt)

        return req.asJson()
    }


    fun getTopic(id: Int): HttpResponse<JsonNode> {
        return getRequest("/t/$id.json").asJson()
    }

    fun getAllTopicsForCategory(id: Int): List<Int> {
        val req = getRequest("/c/$id.json")
        val json = req.asJson()

        if (200 != json.status) {
            return listOf()
        }

        val mapper = jacksonObjectMapper()
        return mapper.readTree(json.body?.toString())["topic_list"]["topics"]
            .map { it["id"].asInt() }
    }
}
