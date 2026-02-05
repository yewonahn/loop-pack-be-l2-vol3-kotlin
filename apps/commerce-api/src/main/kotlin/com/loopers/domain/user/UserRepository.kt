package com.loopers.domain.user

interface UserRepository {
    fun findById(id: Long): User?
    fun findByLoginId(loginId: String): User?
    fun existsByLoginId(loginId: String): Boolean
    fun save(user: User): User
}
