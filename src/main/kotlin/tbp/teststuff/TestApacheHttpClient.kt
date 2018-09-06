package tbp.teststuff

import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.util.EntityUtils

/**
 * Apache http client seems more difficult to use compared to Unirest.
 * G'bye Apache.
 */
fun main(args: Array<String>) {
    val client = HttpClientBuilder.create().build()

    val response = client.execute(HttpGet("https://chat.tbp.land/admin/users/list/active.json")
//        .

    )!!

    println(EntityUtils.toString(response.entity))






//    println(request.url)


//    println(request.asJson().body)

}
