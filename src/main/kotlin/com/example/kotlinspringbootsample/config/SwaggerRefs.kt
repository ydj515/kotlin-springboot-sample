package com.example.kotlinspringbootsample.config

object SwaggerRefs {
    const val BAD_REQUEST_RESPONSE_NAME = "BadRequestResponse"
    const val NOT_FOUND_RESPONSE_NAME = "NotFoundResponse"
    const val CONFLICT_RESPONSE_NAME = "ConflictResponse"

    const val BAD_REQUEST_RESPONSE_REF = "#/components/responses/$BAD_REQUEST_RESPONSE_NAME"
    const val NOT_FOUND_RESPONSE_REF = "#/components/responses/$NOT_FOUND_RESPONSE_NAME"
    const val CONFLICT_RESPONSE_REF = "#/components/responses/$CONFLICT_RESPONSE_NAME"

    const val SIGNUP_REQUEST_EXAMPLE_NAME = "SignupRequest"
    const val SIGNUP_REQUEST_EXAMPLE_REF = "#/components/examples/$SIGNUP_REQUEST_EXAMPLE_NAME"
    const val SIGNUP_SUCCESS_EXAMPLE_NAME = "SignupSuccess"
    const val SIGNUP_SUCCESS_EXAMPLE_REF = "#/components/examples/$SIGNUP_SUCCESS_EXAMPLE_NAME"
    const val SIGNUP_VALIDATION_FAILURE_EXAMPLE_NAME = "SignupValidationFailure"
    const val SIGNUP_VALIDATION_FAILURE_EXAMPLE_REF =
        "#/components/examples/$SIGNUP_VALIDATION_FAILURE_EXAMPLE_NAME"
    const val SIGNUP_ALREADY_EXISTS_FAILURE_EXAMPLE_NAME = "SignupAlreadyExistsFailure"
    const val SIGNUP_ALREADY_EXISTS_FAILURE_EXAMPLE_REF =
        "#/components/examples/$SIGNUP_ALREADY_EXISTS_FAILURE_EXAMPLE_NAME"

    const val ORDER_CREATE_REQUEST_EXAMPLE_NAME = "OrderCreateRequest"
    const val ORDER_CREATE_REQUEST_EXAMPLE_REF = "#/components/examples/$ORDER_CREATE_REQUEST_EXAMPLE_NAME"
    const val ORDER_STATUS_SUMMARY_SUCCESS_EXAMPLE_NAME = "OrderStatusSummarySuccess"
    const val ORDER_STATUS_SUMMARY_SUCCESS_EXAMPLE_REF =
        "#/components/examples/$ORDER_STATUS_SUMMARY_SUCCESS_EXAMPLE_NAME"
    const val ORDER_LIST_SUCCESS_EXAMPLE_NAME = "OrderListSuccess"
    const val ORDER_LIST_SUCCESS_EXAMPLE_REF = "#/components/examples/$ORDER_LIST_SUCCESS_EXAMPLE_NAME"
    const val ORDER_DETAIL_SUCCESS_EXAMPLE_NAME = "OrderDetailSuccess"
    const val ORDER_DETAIL_SUCCESS_EXAMPLE_REF = "#/components/examples/$ORDER_DETAIL_SUCCESS_EXAMPLE_NAME"
    const val ORDER_CREATE_SUCCESS_EXAMPLE_NAME = "OrderCreateSuccess"
    const val ORDER_CREATE_SUCCESS_EXAMPLE_REF = "#/components/examples/$ORDER_CREATE_SUCCESS_EXAMPLE_NAME"
    const val ORDER_PAY_SUCCESS_EXAMPLE_NAME = "OrderPaySuccess"
    const val ORDER_PAY_SUCCESS_EXAMPLE_REF = "#/components/examples/$ORDER_PAY_SUCCESS_EXAMPLE_NAME"
    const val ORDER_PAY_PROCESSING_EXAMPLE_NAME = "OrderPayProcessing"
    const val ORDER_PAY_PROCESSING_EXAMPLE_REF = "#/components/examples/$ORDER_PAY_PROCESSING_EXAMPLE_NAME"
    const val ORDER_SHIP_SUCCESS_EXAMPLE_NAME = "OrderShipSuccess"
    const val ORDER_SHIP_SUCCESS_EXAMPLE_REF = "#/components/examples/$ORDER_SHIP_SUCCESS_EXAMPLE_NAME"
    const val ORDER_CANCEL_SUCCESS_EXAMPLE_NAME = "OrderCancelSuccess"
    const val ORDER_CANCEL_SUCCESS_EXAMPLE_REF = "#/components/examples/$ORDER_CANCEL_SUCCESS_EXAMPLE_NAME"
    const val INVALID_REQUEST_FAILURE_EXAMPLE_NAME = "InvalidRequestFailure"
    const val INVALID_REQUEST_FAILURE_EXAMPLE_REF =
        "#/components/examples/$INVALID_REQUEST_FAILURE_EXAMPLE_NAME"
    const val INVALID_ORDER_ITEM_FAILURE_EXAMPLE_NAME = "InvalidOrderItemFailure"
    const val INVALID_ORDER_ITEM_FAILURE_EXAMPLE_REF =
        "#/components/examples/$INVALID_ORDER_ITEM_FAILURE_EXAMPLE_NAME"
    const val ORDER_NOT_FOUND_FAILURE_EXAMPLE_NAME = "OrderNotFoundFailure"
    const val ORDER_NOT_FOUND_FAILURE_EXAMPLE_REF =
        "#/components/examples/$ORDER_NOT_FOUND_FAILURE_EXAMPLE_NAME"
    const val ORDER_CONFLICT_FAILURE_EXAMPLE_NAME = "OrderConflictFailure"
    const val ORDER_CONFLICT_FAILURE_EXAMPLE_REF =
        "#/components/examples/$ORDER_CONFLICT_FAILURE_EXAMPLE_NAME"
    const val OPTIMISTIC_LOCK_FAILURE_EXAMPLE_NAME = "OptimisticLockFailure"
    const val OPTIMISTIC_LOCK_FAILURE_EXAMPLE_REF =
        "#/components/examples/$OPTIMISTIC_LOCK_FAILURE_EXAMPLE_NAME"
}
