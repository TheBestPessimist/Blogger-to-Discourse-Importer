package tbp.teststuff

import tbp.discourse.client.DiscourseClient
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

private lateinit var API_KEY: String
private lateinit var API_USERNAME: String

private lateinit var discourse: DiscourseClient


fun main(args: Array<String>) {
    loadApiCredentials()
    discourse = DiscourseClient(API_KEY, API_USERNAME, "https://chat.tbp.land/")

    println(discourse.getLatest().body)

//    discourse.createNewCategory("penis", "AAAAAA", "FFFFFF")



}


private fun loadApiCredentials(): Unit {
    val props = Properties()
    props.load(Files.newBufferedReader(Paths.get("resources/discourse.api.key.properties")))
    API_KEY = props.getProperty("api_key")
    API_USERNAME = props.getProperty("api_username")
}
