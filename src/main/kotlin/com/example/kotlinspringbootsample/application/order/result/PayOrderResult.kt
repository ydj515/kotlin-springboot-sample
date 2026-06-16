package com.example.kotlinspringbootsample.application.order.result

enum class PayOrderOutcomeStatus {
    PAID,
    PROCESSING,
    CANCELING,
    CANCELED
}

data class PayOrderResult(
    val status: PayOrderOutcomeStatus,
    val message: String,
    val pollingUrl: String,
    val order: OrderResult
) {
    companion object {
        fun paid(order: OrderResult): PayOrderResult =
            PayOrderResult(
                status = PayOrderOutcomeStatus.PAID,
                message = "결제 및 주문이 완료되었습니다.",
                pollingUrl = "/api/orders/${order.id}",
                order = order
            )

        fun processing(order: OrderResult): PayOrderResult =
            PayOrderResult(
                status = PayOrderOutcomeStatus.PROCESSING,
                message = "결제는 승인되었고 주문을 처리 중입니다. 잠시 후 주문 내역에서 확인할 수 있습니다.",
                pollingUrl = "/api/orders/${order.id}",
                order = order
            )

        fun canceling(order: OrderResult): PayOrderResult =
            PayOrderResult(
                status = PayOrderOutcomeStatus.CANCELING,
                message = "결제는 승인되었지만 주문 처리에 실패해 결제 취소를 진행 중입니다.",
                pollingUrl = "/api/orders/${order.id}",
                order = order
            )

        fun canceled(order: OrderResult): PayOrderResult =
            PayOrderResult(
                status = PayOrderOutcomeStatus.CANCELED,
                message = "주문 처리 실패로 결제가 취소되었습니다.",
                pollingUrl = "/api/orders/${order.id}",
                order = order
            )
    }
}
