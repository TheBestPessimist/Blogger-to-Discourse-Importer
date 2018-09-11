package tbp.blogger

import java.time.LocalDateTime

data class Blog(val name: String, val posts: MutableList<Post>)

data class Post(val title: String, val content: String, val comments: MutableList<Comment>, val date: LocalDateTime, val tags: MutableSet<String>, val bloggerURL: String) {
//    override fun toString(): String {
//        return "Post(title='$title', comments=$comments, date=$date, tags=$tags, bloggerURL='$bloggerURL') \n"
//    }
}


data class Comment(val author: String, val content: String, val date: LocalDateTime)
