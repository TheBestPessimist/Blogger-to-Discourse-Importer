package tbp.discourse

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.treeToValue
import io.github.openunirest.http.HttpResponse
import io.github.openunirest.http.JsonNode
import io.github.openunirest.http.Unirest
import io.github.openunirest.request.GetRequest
import io.github.openunirest.request.HttpRequestWithBody
import org.apache.http.entity.ContentType
import java.io.InputStream
import java.net.URI
import java.time.LocalDateTime


@Suppress("unused", "MemberVisibilityCanBePrivate")
class DiscourseClient(apiKey: String, apiUsername: String, baseUrl: String) {

    private val rb: DiscourseRequestBuilder = DiscourseRequestBuilder(apiKey, apiUsername, baseUrl)

    ////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    //              topic

    fun getLatestTopics(): HttpResponse<JsonNode> {
        val link = "/latest.json"
        return rb.getRequest(link, emptyMap()).asJson()
    }


    fun getTopic(id: Int): HttpResponse<JsonNode> {
        return rb.getRequest("/t/$id.json", emptyMap()).asJson()
    }

    fun createNewTopic(title: String, rawContent: String, categoryId: Int, createdAt: LocalDateTime): HttpResponse<JsonNode> {
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

        val formData = mapOf(
            "title" to title,
            "category" to categoryId,
            "created_at" to createdAt,
            "raw" to rawContent
        )
        val req = rb.postRequest("/posts.json", formData)

        return req.asJson()
    }


    fun deleteTopic(id: Int): HttpResponse<JsonNode> {
        val req = rb.deleteRequest("/t/$id.json")
        val json = req.asJson()

//        with(json) {
//            if (200 != status) {
//                var s = "$status $statusText $body "
//                println(s)
//            }
//        }


        return json
    }


    ////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    //              category

    fun createNewCategory(name: String, color: String, textColor: String, adminOnly: Boolean): HttpResponse<JsonNode> {
        val link = "/categories.json"
        val formData: MutableMap<String, Any> = mutableMapOf(
            "name" to name,
            "color" to color,
            "text_color" to textColor
        )
        if (adminOnly) {
            formData["permissions[admins]"] = 1
        }

        val req = rb.postRequest(link, formData)

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

    /**
     * The response may be paged =>
     * there may be more posts on the next page
     */
    fun getAllTopicsForCategory(id: Int): List<Int> {
        var reqLink = "/c/$id.json"
        val topicIDs = mutableSetOf<Int>()

        while (!reqLink.isNullOrEmpty()) {
            val req = rb.getRequest(reqLink, emptyMap())
            val json = req.asJson()

            if (200 != json.status) {
                return listOf()
            }

            val mapper = jacksonObjectMapper()
            val topicList = mapper.readTree(json.body?.toString())["topic_list"]

            reqLink = if (!topicList["more_topics_url"]?.textValue().isNullOrEmpty()) {
                topicList["more_topics_url"].textValue().replace("?", ".json?")
            } else {
                ""
            }

            topicIDs.addAll(topicList["topics"].map { it["id"].asInt() })
        }
        return topicIDs.toList()
    }


    ////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    //              search


    /**
     * Return the category ID if exists -1 otherwise
     */
    fun searchCategoryByName(name: String): Int {
        val link = "/categories.json"
        val req = rb.getRequest(link, emptyMap())

//        with(req.asJson()) {
//            if (200 != status) {
//                println("$status $statusText $body")
//                return -1
//            }
//        }

        val mapper = jacksonObjectMapper()
        val jsonNode = mapper.readTree(req.asJson().body.toString())

        val treeToValue =
            mapper.treeToValue<Array<DiscourseCategory>>(jsonNode["category_list"]["categories"])

        return treeToValue.firstOrNull { name.equals(it.name, true) }?.id ?: -1
    }


    fun searchTopicByTitle(title: String): Int {
        val link = "/search.json"

        val queryString = mapOf(
            "q" to title
        )

        val req = rb.getRequest(link, queryString)
        val json = req.asJson()

        val mapper = jacksonObjectMapper()
        return mapper.readTree(json.body.toString())["topics"]
            .firstOrNull { title.equals(it["title"].asText(), true) }
            ?.get("id")?.asInt() ?: -1
    }


    ////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    //              upload

    /**
     * upload a file and return the usable url
     */
    fun uploadFile(fileInputStream: InputStream, fileName: String): String {
        val request = Unirest.post("https://chat.tbp.land/uploads.json")
        request
            .header("Accept", "application/json")

        request.field("files[]", fileInputStream, ContentType.DEFAULT_BINARY, fileName)
            .field("type", "composer", ContentType.TEXT_PLAIN.mimeType)
            .field("synchronous", "true", ContentType.TEXT_PLAIN.mimeType)
            .field("api_key", rb.apiKey, ContentType.TEXT_PLAIN.mimeType)
            .field("api_username", rb.apiUsername, ContentType.TEXT_PLAIN.mimeType)

        val json = request.asJson()

        val jsonTree = jacksonObjectMapper().readTree(json.body.toString())
        val url = jsonTree["short_url"].asText()
        val title = jsonTree["original_filename"].asText()

        return "![$title]($url)"
    }
}

@Suppress("MemberVisibilityCanBePrivate")
class DiscourseRequestBuilder(val apiKey: String, val apiUsername: String, val baseUrl: String) {
    /**
     * Generic POST builder
     */
    fun postRequest(link: String, formData: Map<String, Any>): HttpRequestWithBody {
        val request = Unirest.post(URI.create("$baseUrl/$link").normalize().toString())
        request.queryString("api_key", apiKey)
            .queryString("api_username", apiUsername)
            .header("Content-Type", "multipart/form-data")
            .header("Accept", "application/json")
//            .fields(formData)

        return request
    }

    /**
     * Generic DELETE builder
     */
    fun deleteRequest(link: String): HttpRequestWithBody {
        val request = Unirest.delete(URI.create("$baseUrl/$link").normalize().toString())
        request.queryString("api_key", apiKey)
            .queryString("api_username", apiUsername)
            .header("Content-Type", "multipart/form-data")
            .header("Accept", "application/json")
        return request
    }

    /**
     * Generic GET builder
     */
    fun getRequest(link: String, queryString: Map<String, String>): GetRequest {
        val request = Unirest.get(URI.create("$baseUrl/$link").normalize().toString())
        request.queryString("api_key", apiKey)
            .queryString("api_username", apiUsername)
            .header("Content-Type", "multipart/form-data")
            .header("Accept", "application/json")
        queryString.forEach { k, v ->
            request.queryString(k, v)
        }

        val json = request.asJson()
        if (json.status == 429) {
            json.dbg()
        }


        return request
    }


}

@Suppress("unused")
fun HttpResponse<JsonNode>.dbg() {
    println(">>>>>>>>>>>\n$headers\n$status: $statusText\n$parsingError\n$body\n<<<<<<<<<<<<<")
}
