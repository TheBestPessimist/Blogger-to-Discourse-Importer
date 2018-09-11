package tbp.discourse

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * This dataclass is used as a pojo for parsing the /categories.json route with jackson.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class DiscourseCategory(
    val id: Int,
    val name: String
)
