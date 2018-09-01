package tbp.blogger

import com.google.api.client.util.DateTime
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Kotlin extension function for datatype com.google.api.client.util.DateTime =>
 * There's a new method available from kotlin: com.google.api.client.util.DateTime.toLocalDateTime
 * which returns a java.time.DateTime.
 *
 * This is created just for my ease of use :D and because kotlin can!
 */
fun DateTime.toLocalDateTime(): LocalDateTime {
    return LocalDateTime.parse(this.toStringRfc3339(), DateTimeFormatter.ISO_DATE_TIME)
}
