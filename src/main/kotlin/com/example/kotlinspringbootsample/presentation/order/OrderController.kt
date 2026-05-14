package com.example.kotlinspringbootsample.presentation.order

import com.example.kotlinspringbootsample.application.order.OrderUseCase
import com.example.kotlinspringbootsample.application.order.command.CancelOrderCommand
import com.example.kotlinspringbootsample.application.order.command.CreateOrderCommand
import com.example.kotlinspringbootsample.application.order.command.CreateOrderItemCommand
import com.example.kotlinspringbootsample.application.order.command.FindOrdersCommand
import com.example.kotlinspringbootsample.application.order.command.FindOrderStatusSummariesCommand
import com.example.kotlinspringbootsample.application.order.command.GetOrderCommand
import com.example.kotlinspringbootsample.application.order.command.OrderSearchMode
import com.example.kotlinspringbootsample.application.order.command.PayOrderCommand
import com.example.kotlinspringbootsample.application.order.command.ShipOrderCommand
import com.example.kotlinspringbootsample.application.order.result.OrderLineResult
import com.example.kotlinspringbootsample.application.order.result.OrderResult
import com.example.kotlinspringbootsample.application.order.result.OrderStatusSummaryResult
import com.example.kotlinspringbootsample.application.order.result.OrderSummaryResult
import com.example.kotlinspringbootsample.config.SwaggerRefs.ORDER_CANCEL_SUCCESS_EXAMPLE_REF
import com.example.kotlinspringbootsample.config.SwaggerRefs.ORDER_CREATE_REQUEST_EXAMPLE_REF
import com.example.kotlinspringbootsample.config.SwaggerRefs.ORDER_CREATE_SUCCESS_EXAMPLE_REF
import com.example.kotlinspringbootsample.config.SwaggerRefs.ORDER_DETAIL_SUCCESS_EXAMPLE_REF
import com.example.kotlinspringbootsample.config.SwaggerRefs.ORDER_LIST_SUCCESS_EXAMPLE_REF
import com.example.kotlinspringbootsample.config.SwaggerRefs.ORDER_PAY_SUCCESS_EXAMPLE_REF
import com.example.kotlinspringbootsample.config.SwaggerRefs.ORDER_SHIP_SUCCESS_EXAMPLE_REF
import com.example.kotlinspringbootsample.config.SwaggerRefs.ORDER_STATUS_SUMMARY_SUCCESS_EXAMPLE_REF
import com.example.kotlinspringbootsample.domain.order.OrderStatus
import com.example.kotlinspringbootsample.presentation.common.ApiResult
import com.example.kotlinspringbootsample.presentation.common.ResultCode
import com.example.kotlinspringbootsample.presentation.order.request.CreateOrderRequest
import com.example.kotlinspringbootsample.presentation.order.response.OrderLineResponse
import com.example.kotlinspringbootsample.presentation.order.response.OrderResponse
import com.example.kotlinspringbootsample.presentation.order.response.OrderStatusSummaryResponse
import com.example.kotlinspringbootsample.presentation.order.response.OrderSummaryResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/orders")
@Tag(name = "Orders", description = "주문 조회와 상태 전이 API")
class OrderController(
    private val orderUseCase: OrderUseCase
) {
    @Operation(
        summary = "주문 상태 요약 조회",
        description = "주문 상태별 개수를 projection 기반으로 조회합니다. customerName, status 조건으로 집계 범위를 줄일 수 있습니다."
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "주문 상태 요약 조회 성공",
                content = [Content(
                    mediaType = "application/json",
                    examples = [ExampleObject(ref = ORDER_STATUS_SUMMARY_SUCCESS_EXAMPLE_REF)]
                )]
            )
        ]
    )
    @GetMapping("/status-summary")
    fun getOrderStatusSummaries(
        @Parameter(description = "고객 이름 필터", example = "한수진")
        @RequestParam(required = false) customerName: String?,
        @Parameter(
            description = "특정 주문 상태만 집계",
            example = "PAID",
            schema = Schema(implementation = OrderStatus::class)
        )
        @RequestParam(required = false) status: OrderStatus?
    ): ApiResult.Success<List<OrderStatusSummaryResponse>> =
        ApiResult.success(
            orderUseCase.getOrderStatusSummaries(
                FindOrderStatusSummariesCommand(
                    customerName = customerName,
                    status = status
                )
            )
                .map(OrderStatusSummaryResult::toResponse)
        )

    @Operation(
        summary = "주문 목록 조회",
        description = "customerName, status 조건으로 주문을 조회합니다. searchMode=DERIVED는 파생 쿼리, searchMode=JPQL은 nullable 조건 JPQL을 사용합니다."
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "주문 목록 조회 성공",
                content = [Content(
                    mediaType = "application/json",
                    examples = [ExampleObject(ref = ORDER_LIST_SUCCESS_EXAMPLE_REF)]
                )]
            )
        ]
    )
    @GetMapping
    fun getOrders(
        @Parameter(description = "페이지 번호", example = "0")
        @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "페이지 크기", example = "10")
        @RequestParam(defaultValue = "10") size: Int,
        @Parameter(description = "고객 이름 필터", example = "한수진")
        @RequestParam(required = false) customerName: String?,
        @Parameter(
            description = "주문 상태 필터",
            example = "PAID",
            schema = Schema(implementation = OrderStatus::class)
        )
        @RequestParam(required = false) status: OrderStatus?,
        @Parameter(
            description = "조회 전략. DERIVED는 파생 쿼리, JPQL은 조건식 기반 JPQL",
            example = "JPQL",
            schema = Schema(implementation = OrderSearchMode::class)
        )
        @RequestParam(defaultValue = "DERIVED") searchMode: OrderSearchMode
    ): ApiResult.Success<Page<OrderSummaryResponse>> =
        ApiResult.success(
            orderUseCase.getOrders(
                FindOrdersCommand(
                    page = page,
                    size = size,
                    customerName = customerName,
                    status = status,
                    searchMode = searchMode
                )
            ).map(OrderSummaryResult::toResponse)
        )

    @Operation(summary = "주문 단건 조회", description = "상세 조회는 EntityGraph 기반으로 customer와 orderLines를 함께 로드합니다.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "주문 상세 조회 성공",
                content = [Content(
                    mediaType = "application/json",
                    examples = [ExampleObject(ref = ORDER_DETAIL_SUCCESS_EXAMPLE_REF)]
                )]
            )
        ]
    )
    @GetMapping("/{id}")
    fun getOrder(
        @Parameter(description = "주문 ID", example = "1")
        @PathVariable id: Long
    ): ApiResult.Success<OrderResponse> =
        ApiResult.success(orderUseCase.getOrder(GetOrderCommand(id)).toResponse())

    @Operation(summary = "주문 생성", description = "주문과 주문 라인을 함께 생성합니다.")
    @SwaggerRequestBody(
        required = true,
        description = "주문 생성 요청 바디",
        content = [Content(
            mediaType = "application/json",
            schema = Schema(implementation = CreateOrderRequest::class),
            examples = [ExampleObject(ref = ORDER_CREATE_REQUEST_EXAMPLE_REF)]
        )]
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "주문 생성 성공",
                content = [Content(
                    mediaType = "application/json",
                    examples = [ExampleObject(ref = ORDER_CREATE_SUCCESS_EXAMPLE_REF)]
                )]
            )
        ]
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    fun createOrder(@RequestBody request: CreateOrderRequest): ApiResult.Success<OrderResponse> =
        ApiResult.success(orderUseCase.createOrder(request.toCommand()).toResponse(), ResultCode.CREATED)

    @Operation(summary = "주문 결제", description = "CREATED 상태 주문을 PAID 상태로 전이합니다.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "주문 결제 성공",
                content = [Content(
                    mediaType = "application/json",
                    examples = [ExampleObject(ref = ORDER_PAY_SUCCESS_EXAMPLE_REF)]
                )]
            )
        ]
    )
    @PostMapping("/{id}/pay")
    fun payOrder(
        @Parameter(description = "주문 ID", example = "2")
        @PathVariable id: Long
    ): ApiResult.Success<OrderResponse> =
        ApiResult.success(orderUseCase.payOrder(PayOrderCommand(id)).toResponse())

    @Operation(summary = "주문 배송", description = "PAID 상태 주문을 SHIPPED 상태로 전이합니다.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "주문 배송 성공",
                content = [Content(
                    mediaType = "application/json",
                    examples = [ExampleObject(ref = ORDER_SHIP_SUCCESS_EXAMPLE_REF)]
                )]
            )
        ]
    )
    @PostMapping("/{id}/ship")
    fun shipOrder(
        @Parameter(description = "주문 ID", example = "2")
        @PathVariable id: Long
    ): ApiResult.Success<OrderResponse> =
        ApiResult.success(orderUseCase.shipOrder(ShipOrderCommand(id)).toResponse())

    @Operation(summary = "주문 취소", description = "CREATED 또는 PAID 상태 주문을 CANCELLED 상태로 전이합니다.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "주문 취소 성공",
                content = [Content(
                    mediaType = "application/json",
                    examples = [ExampleObject(ref = ORDER_CANCEL_SUCCESS_EXAMPLE_REF)]
                )]
            )
        ]
    )
    @PostMapping("/{id}/cancel")
    fun cancelOrder(
        @Parameter(description = "주문 ID", example = "2")
        @PathVariable id: Long
    ): ApiResult.Success<OrderResponse> =
        ApiResult.success(orderUseCase.cancelOrder(CancelOrderCommand(id)).toResponse())
}

private fun CreateOrderRequest.toCommand(): CreateOrderCommand =
    CreateOrderCommand(
        customerId = customerId,
        recipient = shippingAddress.recipient,
        zipCode = shippingAddress.zipCode,
        address1 = shippingAddress.address1,
        address2 = shippingAddress.address2,
        deliveryRequestedAt = deliveryRequestedAt,
        items = items.map {
            CreateOrderItemCommand(
                productName = it.productName,
                quantity = it.quantity,
                unitPrice = it.unitPrice
            )
        }
    )

private fun OrderSummaryResult.toResponse(): OrderSummaryResponse =
    OrderSummaryResponse(
        id = id,
        version = version,
        orderNo = orderNo,
        customerId = customerId,
        customerName = customerName,
        status = status,
        totalAmount = totalAmount,
        orderedAt = orderedAt,
        paidAt = paidAt,
        shippedAt = shippedAt,
        cancelledAt = cancelledAt,
        createdAt = createdAt
    )

private fun OrderResult.toResponse(): OrderResponse =
    OrderResponse(
        id = id,
        version = version,
        orderNo = orderNo,
        customerId = customerId,
        customerName = customerName,
        status = status,
        recipient = recipient,
        zipCode = zipCode,
        address1 = address1,
        address2 = address2,
        totalAmount = totalAmount,
        items = items.map(OrderLineResult::toResponse),
        orderedAt = orderedAt,
        deliveryRequestedAt = deliveryRequestedAt,
        paidAt = paidAt,
        shippedAt = shippedAt,
        cancelledAt = cancelledAt,
        trackingNumber = trackingNumber,
        cancelReason = cancelReason,
        createdAt = createdAt
    )

private fun OrderLineResult.toResponse(): OrderLineResponse =
    OrderLineResponse(
        productName = productName,
        quantity = quantity,
        unitPrice = unitPrice,
        lineAmount = lineAmount
    )

private fun OrderStatusSummaryResult.toResponse(): OrderStatusSummaryResponse =
    OrderStatusSummaryResponse(
        status = status,
        count = count
    )
