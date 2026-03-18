package com.loopers.domain.order

interface OrderRepository {
    fun findByIdOrNull(id: Long): Order?
    fun save(order: Order): Order
}
