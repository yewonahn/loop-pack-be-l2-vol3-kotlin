package com.loopers.infrastructure.order

import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderRepository
import org.springframework.stereotype.Repository

@Repository
class OrderRepositoryImpl(
    private val orderJpaRepository: OrderJpaRepository,
) : OrderRepository {

    override fun findByIdOrNull(id: Long): Order? {
        return orderJpaRepository.findById(id).orElse(null)
    }

    override fun save(order: Order): Order {
        return orderJpaRepository.save(order)
    }
}
