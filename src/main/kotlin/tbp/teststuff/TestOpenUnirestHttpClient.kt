package tbp.teststuff

import io.github.openunirest.http.Unirest
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

private lateinit var API_KEY: String
private lateinit var API_USERNAME: String


fun main(args: Array<String>) {
    loadApiCredentials()


    val request = Unirest.get("https://chat.tbp.land/admin/users/list/active.json")
    request.queryString("api_key", API_KEY)
    request.queryString("api_username", API_USERNAME)

    println(request.url)


    println(request.asJson().body)
}

private fun loadApiCredentials(): Unit {
    val props = Properties()
    props.load(Files.newBufferedReader(Paths.get("resources/discourse.api.key.properties")))
    API_KEY = props.getProperty("api_key")
    API_USERNAME = props.getProperty("api_username")
}
