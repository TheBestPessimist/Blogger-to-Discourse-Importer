package tbp.discourse.client

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.treeToValue
import io.github.openunirest.http.HttpResponse
import io.github.openunirest.http.JsonNode
import io.github.openunirest.http.Unirest
import io.github.openunirest.request.GetRequest
import io.github.openunirest.request.HttpRequestWithBody
import java.net.URI

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

    fun deleteCategory(id: Int): HttpResponse<JsonNode> {
        /**
         * i would like here to be able to use
         * req.routeParam()
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
        val link = "/categories/${id}"
        val req = deleteRequest(link)

        return req.asJson()
    }

    /**
     * Return the category ID if exists -1 otherwise
     */
    fun searchCategoryByName(name: String): Int {
        val link = "/categories.json"
        val req = getRequest(link)

        val mapper = jacksonObjectMapper()
        val treeToValue =
            mapper.treeToValue<Array<DiscourseCategory>>(mapper.readTree(req.asJson().body.toString())["category_list"]["categories"])

        return treeToValue.firstOrNull { it.name == name }?.id ?: -1
    }


    /**
     * Generic POST builder
     */
    private fun postRequest(link: String): HttpRequestWithBody {
        val request = Unirest.post(URI.create("$baseUrl/$link").normalize().toString())
        request.queryString("api_key", apiKey)
        request.queryString("api_username", apiUsername)
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


}
