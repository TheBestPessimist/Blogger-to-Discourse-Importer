package tbp.discourse.client

import io.github.openunirest.http.HttpResponse
import io.github.openunirest.http.JsonNode
import io.github.openunirest.http.Unirest
import io.github.openunirest.request.GetRequest
import io.github.openunirest.request.HttpRequestWithBody

class DiscourseClient(val apiKey: String, val apiUsername: String, val baseUrl: String) {

    fun getLatest(): HttpResponse<JsonNode> {
        val link = "latest.json"
        return getRequest(link).asJson()
    }

    fun createNewCategory(name: String, color: String, textColor: String): HttpResponse<JsonNode> {
        val link = "categories.json"
        val req = postRequest(link)
        req.queryString("name", name)
        req.queryString("color", color)
        req.queryString("text_color", textColor)

        return req.asJson()
    }


    /**
     * Generic POST builder
     */
    private fun postRequest(link: String): HttpRequestWithBody {
        val request = Unirest.post("$baseUrl/$link")
        request.queryString("apiKey", apiKey)
        request.queryString("api_username", apiUsername)
        return request
    }

    /**
     * Generic GET builder
     */
    private fun getRequest(link: String): GetRequest {
        val request = Unirest.get("$baseUrl/$link")
        request.queryString("apiKey", apiKey)
        request.queryString("api_username", apiUsername)
        return request
    }


}
