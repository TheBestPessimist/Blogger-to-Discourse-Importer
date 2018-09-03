package tbp.teststuff

import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.util.EntityUtils

fun main(args: Array<String>) {
    val client = HttpClientBuilder.create().build()

    val response = client.execute(HttpGet("https://chat.tbp.land"))!!
    println(EntityUtils.toString(response.entity))
}
