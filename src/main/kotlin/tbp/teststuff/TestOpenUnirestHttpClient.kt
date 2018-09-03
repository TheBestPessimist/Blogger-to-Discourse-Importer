package tbp.teststuff

import io.github.openunirest.http.Unirest

fun main(args: Array<String>) {
    val request = Unirest.get("https://chat.tbp.land/latest.json")

    println(request.asJson().body)
}
